package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupOutcome;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.GroupBuild;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})
public final class ExpandGroupsPhase implements ExpansionPhase {

    static final int MAX_OUTER_PASSES = 32;
    private static final int SINGLE_SLOT = 1;

    private final List<Bridge> bridges;
    private final List<GroupTarget> groupTargets;
    private final List<PathSegmentResolver> pathSegmentResolvers;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver nullabilityResolver;

    /**
     * Engine-internal side-map recording, for each Node typed by {@link Node#setTyping(TypeMirror, Nullability)}
     * during this {@code apply()} call, the {@link Element} scope that drove the resolver call. Populated at
     * every producer-commit site; consumed at the directive-binding propagation site so it can re-invoke
     * {@link NullabilityResolver#resolve} rather than carry an opaque {@link Nullability} forward.
     */
    @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
    private final IdentityHashMap<Node, javax.lang.model.element.@Nullable Element> producerScopes =
            new IdentityHashMap<>();

    @Override
    public void apply(final MapperGraph graph) {
        producerScopes.clear();
        final var pathResolver = new PathSegmentGroupResolver(pathSegmentResolvers);
        final var satGroups = new IdentityHashMap<ExpansionGroup, Boolean>();
        final var pendingFailures = new IdentityHashMap<ExpansionGroup, Node>();

        for (var pass = 0; pass < MAX_OUTER_PASSES; pass++) {
            final var snapshot = graph.groups().collect(Collectors.toList());
            final var change = new ChangeTracker();
            for (final var group : snapshot) {
                if (satGroups.containsKey(group)) {
                    continue;
                }
                final var stepResult = fillGroup(group, graph, pathResolver, satGroups, change);
                if (stepResult.outcome == GroupOutcome.Kind.SAT) {
                    satGroups.put(group, Boolean.TRUE);
                    pendingFailures.remove(group);
                    graph.recordGroupOutcome(GroupOutcome.sat(group));
                    change.markSat();
                } else if (stepResult.failingSlot != null) {
                    pendingFailures.put(group, stepResult.failingSlot);
                }
            }
            if (!change.changed) {
                break;
            }
        }

        recordUnsatOutcomes(graph, satGroups, pendingFailures);
    }

    private void recordUnsatOutcomes(
            final MapperGraph graph,
            final Map<ExpansionGroup, Boolean> satGroups,
            final Map<ExpansionGroup, Node> pendingFailures) {
        final var allGroups = graph.groups().collect(Collectors.toList());
        for (final var group : allGroups) {
            if (satGroups.containsKey(group)) {
                continue;
            }
            final var failingSlot = pendingFailures.getOrDefault(
                    group,
                    group.getSlots().isEmpty()
                            ? group.getRoot()
                            : group.getSlots().get(0));
            graph.recordGroupOutcome(GroupOutcome.unsatNoPlan(group, failingSlot));
        }
    }

    private StepResult fillGroup(
            final ExpansionGroup group,
            final MapperGraph graph,
            final PathSegmentGroupResolver pathResolver,
            final Map<ExpansionGroup, Boolean> satGroups,
            final ChangeTracker change) {
        if (PathSegmentGroupResolver.isPathSegmentGroup(group)) {
            return expandPathSegmentGroup(group, graph, pathResolver, change);
        }
        if (isDirectiveBindingGroup(group)) {
            return expandDirectiveBindingGroup(group, graph, satGroups, change);
        }
        return expandBridgeGroup(group, graph, satGroups, change);
    }

    private static boolean isDirectiveBindingGroup(final ExpansionGroup group) {
        if (group.getSlots().size() != SINGLE_SLOT) {
            return false;
        }
        return group.getRoot().getLoc() instanceof TargetLocation
                && group.getSlots().get(0).getLoc() instanceof SourceLocation;
    }

    private StepResult expandDirectiveBindingGroup(
            final ExpansionGroup group,
            final MapperGraph graph,
            final Map<ExpansionGroup, Boolean> satGroups,
            final ChangeTracker change) {
        final var slot = group.getSlots().get(0);
        final var slotState = resolveSlot(slot, group, graph, satGroups, change);
        if (slotState != SlotState.SAT) {
            return StepResult.pending(slot);
        }
        final var root = group.getRoot();
        if (root.getType().isEmpty() && slot.getType().isPresent()) {
            // Directive-binding propagates the source-leaf's producer commitment to the target-leaf.
            // We re-invoke the resolver with the source-leaf's recorded producer scope so this site
            // structurally follows the "every setTyping passes a resolver-derived Nullability" rule.
            // Scope lookup falls back to a SourceLocation-derived parameter element when the source
            // was typed outside this phase (e.g., by SeedGraph for a parameter root).
            final var scope = scopeFor(slot);
            final var type = slot.getType().get();
            final var nullability = scope == null ? Nullability.UNKNOWN : nullabilityResolver.resolve(type, scope);
            root.setTyping(type, nullability);
            producerScopes.put(root, scope);
            change.markTypeAssigned();
        }
        if (root.getType().isEmpty()) {
            return StepResult.pending(root);
        }
        if (slot.getType().isPresent()
                && resolveCtx
                        .types()
                        .isSameType(slot.getType().get(), root.getType().get())) {
            ensureDirectAssignEdge(graph, group, slot, root, change);
            return StepResult.sat();
        }
        if (hasSatChildAt(root, satGroups)) {
            return StepResult.sat();
        }
        if (!hasAnyChildAt(root, group, graph)) {
            final var newNodes = new ArrayList<Node>();
            expandFrontier(root, group, graph, change, newNodes);
            if (hasSatChildAt(root, satGroups)) {
                return StepResult.sat();
            }
        }
        return StepResult.pending(slot);
    }

    private void ensureDirectAssignEdge(
            final MapperGraph graph,
            final ExpansionGroup group,
            final Node slot,
            final Node root,
            final ChangeTracker change) {
        final var existing = graph.edges()
                .filter(e -> e.getKind() == io.github.joke.percolate.processor.graph.EdgeKind.REALISED)
                .anyMatch(e -> e.getFrom().equals(slot) && e.getTo().equals(root));
        if (existing) {
            return;
        }
        final io.github.joke.percolate.spi.EdgeCodegen codegen =
                (vars, inputs) -> com.palantir.javapoet.CodeBlock.of("$L", inputs.single());
        final var edge = Edge.realised(
                slot,
                root,
                io.github.joke.percolate.processor.graph.Weights.NOOP,
                codegen,
                "io.github.joke.percolate.processor.stages.expand.DirectiveBinding");
        if (graph.addEdge(edge)) {
            group.addEdgeToView(edge);
            change.markEdgeAdded();
        }
    }

    private boolean hasAnyChildAt(final Node node, final ExpansionGroup excluding, final MapperGraph graph) {
        return graph.groups().anyMatch(g -> !excluding.equals(g) && g.getRoot().equals(node));
    }

    private StepResult expandPathSegmentGroup(
            final ExpansionGroup group,
            final MapperGraph graph,
            final PathSegmentGroupResolver pathResolver,
            final ChangeTracker change) {
        final var slot = group.getSlots().get(0);
        if (slot.getType().isEmpty()) {
            return StepResult.pending(slot);
        }
        final var match = pathResolver.resolveFor(group, resolveCtx);
        if (match.isEmpty()) {
            return StepResult.failed(slot);
        }
        final var rs = match.get().segment;
        final var root = group.getRoot();
        if (root.getType().isEmpty()) {
            final var producerScope = scopeOf(rs.getProducedFrom());
            final var nullability = producerScope == null
                    ? Nullability.UNKNOWN
                    : nullabilityResolver.resolve(rs.getReturnType(), producerScope);
            root.setTyping(rs.getReturnType(), nullability);
            producerScopes.put(root, producerScope);
            change.markTypeAssigned();
        }
        final var edge = Edge.realised(slot, root, rs.getWeight(), rs.getCodegen(), match.get().resolverClassName);
        if (graph.addEdge(edge)) {
            group.addEdgeToView(edge);
            change.markEdgeAdded();
        }
        return StepResult.sat();
    }

    private StepResult expandBridgeGroup(
            final ExpansionGroup group,
            final MapperGraph graph,
            final Map<ExpansionGroup, Boolean> satGroups,
            final ChangeTracker change) {
        var allSat = true;
        Node firstUnsat = null;
        for (final var slot : group.getSlots()) {
            final var slotState = resolveSlot(slot, group, graph, satGroups, change);
            if (slotState == SlotState.SAT) {
                continue;
            }
            allSat = false;
            if (firstUnsat == null) {
                firstUnsat = slot;
            }
        }
        if (!allSat) {
            return StepResult.pending(firstUnsat != null ? firstUnsat : group.getRoot());
        }
        return StepResult.sat();
    }

    private enum SlotState {
        SAT,
        PENDING
    }

    private SlotState resolveSlot(
            final Node slot,
            final ExpansionGroup group,
            final MapperGraph graph,
            final Map<ExpansionGroup, Boolean> satGroups,
            final ChangeTracker change) {
        if (isParameterRootSlot(slot)) {
            return SlotState.SAT;
        }
        if (hasSatChildAt(slot, satGroups)) {
            return SlotState.SAT;
        }
        // Slot Nodes from GroupTarget builds (top-level and nested) start untyped; their expansion
        // is driven by the group-recorded expected type (Slot.type). Skip the empty-type gate when
        // an expected type is available, so producer commits can type the slot at edge-commit time.
        if (slot.getType().isEmpty() && group.expectedTypeFor(slot) == null) {
            return SlotState.PENDING;
        }
        if (!hasAnyChildAt(slot, group, graph)) {
            final var newNodes = new ArrayList<Node>();
            expandFrontier(slot, group, graph, change, newNodes);
            if (hasSatChildAt(slot, satGroups)) {
                return SlotState.SAT;
            }
        }
        return SlotState.PENDING;
    }

    private boolean isParameterRootSlot(final Node slot) {
        if (!(slot.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var segments = ((SourceLocation) slot.getLoc()).getPath().getSegments();
        if (segments.size() != SINGLE_SLOT) {
            return false;
        }
        final var method = resolveCtx.currentMethod();
        if (method == null) {
            return slot.getType().isPresent();
        }
        final var paramName = segments.get(0);
        return method.getParameters().stream()
                .anyMatch(p -> p.getSimpleName().toString().equals(paramName));
    }

    private boolean hasSatChildAt(final Node slot, final Map<ExpansionGroup, Boolean> satGroups) {
        return satGroups.entrySet().stream()
                .anyMatch(entry -> entry.getKey().getRoot().equals(slot));
    }

    private void expandFrontier(
            final Node frontier,
            final ExpansionGroup group,
            final MapperGraph graph,
            final ChangeTracker change,
            final List<Node> newNodes) {
        final var frontierType = effectiveType(group, frontier);
        if (frontierType == null) {
            return;
        }
        final var candidates = Candidates.fromView(group, frontier);
        if (tryBridges(frontier, frontierType, candidates, group, graph, change, newNodes)) {
            return;
        }
        tryGroupTargets(frontier, frontierType, graph, change);
    }

    /**
     * Returns the frontier's effective type for candidate search: the producer-stamped {@code type} if
     * present; otherwise the group-recorded {@link ExpansionGroup#expectedTypeFor expected type} (set by
     * GroupTarget builds for slot Nodes that follow the producer-commit lifecycle). Returns {@code null}
     * if neither is available.
     */
    private static @Nullable TypeMirror effectiveType(final ExpansionGroup group, final Node frontier) {
        final var typed = frontier.getType().orElse(null);
        return typed != null ? typed : group.expectedTypeFor(frontier);
    }

    private boolean tryBridges(
            final Node frontier,
            final TypeMirror frontierType,
            final List<Node> candidates,
            final ExpansionGroup group,
            final MapperGraph graph,
            final ChangeTracker change,
            final List<Node> newNodes) {
        var anyMatched = false;
        for (final var bridge : bridges) {
            for (final var candidate : candidates) {
                final var match = tryBridgeOnCandidate(bridge, candidate, frontier, frontierType, group, graph, change);
                if (match.matched) {
                    if (match.allocated != null) {
                        newNodes.add(match.allocated);
                    }
                    anyMatched = true;
                    break;
                }
            }
        }
        return anyMatched;
    }

    private BridgeMatch tryBridgeOnCandidate(
            final Bridge bridge,
            final Node candidate,
            final Node frontier,
            final TypeMirror frontierType,
            final ExpansionGroup group,
            final MapperGraph graph,
            final ChangeTracker change) {
        final var candidateType = candidate.getType().orElse(null);
        if (candidateType == null) {
            return BridgeMatch.noMatch();
        }
        final var steps = bridge.bridge(candidateType, frontierType, resolveCtx).collect(Collectors.toList());
        for (final var step : steps) {
            if (!resolveCtx.types().isSameType(step.getOutputType(), frontierType)) {
                continue;
            }
            if (!stepMatchesFrontierScope(step, frontier)) {
                continue;
            }
            final var result = commitBridgeStep(
                    graph, group, frontier, step, bridge.getClass().getName(), change);
            if (result.success) {
                return new BridgeMatch(true, result.allocated);
            }
        }
        return BridgeMatch.noMatch();
    }

    private static javax.lang.model.element.@Nullable Element scopeOf(
            final javax.lang.model.AnnotatedConstruct construct) {
        return construct instanceof javax.lang.model.element.Element
                ? (javax.lang.model.element.Element) construct
                : null;
    }

    /**
     * Recovers the {@link Element} scope under which {@code node} was typed, so propagation sites can
     * re-invoke the resolver instead of carrying an opaque {@link Nullability} forward. Prefers the
     * engine-recorded {@code producerScopes} entry; falls back to a SourceLocation-derived parameter
     * element when the node was typed outside this phase (e.g., SeedGraph parameter roots).
     */
    private javax.lang.model.element.@Nullable Element scopeFor(final Node node) {
        if (producerScopes.containsKey(node)) {
            return producerScopes.get(node);
        }
        if (!(node.getLoc() instanceof SourceLocation)) {
            return null;
        }
        final var segments = ((SourceLocation) node.getLoc()).getPath().getSegments();
        if (segments.size() != SINGLE_SLOT) {
            return null;
        }
        final var method = resolveCtx.currentMethod();
        if (method == null) {
            return null;
        }
        final var paramName = segments.get(0);
        for (final var param : method.getParameters()) {
            if (param.getSimpleName().toString().equals(paramName)) {
                return param;
            }
        }
        return null;
    }

    private static boolean stepMatchesFrontierScope(final BridgeStep step, final Node frontier) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return true;
            case ENTERING:
                return frontier.getLoc() instanceof ElementLocation;
            case EXITING:
                return !(frontier.getLoc() instanceof ElementLocation);
        }
        throw new IllegalStateException("Unknown scope transition: " + step.getScopeTransition());
    }

    private void tryGroupTargets(
            final Node frontier, final TypeMirror frontierType, final MapperGraph graph, final ChangeTracker change) {
        for (final var groupTarget : groupTargets) {
            final var build = groupTarget.buildFor(frontierType, List.of(), resolveCtx);
            if (build.isPresent()) {
                registerNestedGroupTarget(
                        frontier,
                        frontierType,
                        build.get(),
                        groupTarget.getClass().getName(),
                        graph,
                        change);
                return;
            }
        }
    }

    private static final class BridgeMatch {
        final boolean matched;
        final @Nullable Node allocated;

        BridgeMatch(final boolean matched, final @Nullable Node allocated) {
            this.matched = matched;
            this.allocated = allocated;
        }

        static BridgeMatch noMatch() {
            return new BridgeMatch(false, null);
        }
    }

    private CommitResult commitBridgeStep(
            final MapperGraph graph,
            final ExpansionGroup parentGroup,
            final Node frontier,
            final BridgeStep step,
            final String strategyFqn,
            final ChangeTracker change) {
        final var allocation = allocateInputNode(graph, parentGroup, frontier, step);
        final var inputNode = allocation.node;
        if (inputNode.equals(frontier)) {
            return CommitResult.skipped();
        }
        final var edge = Edge.realised(inputNode, frontier, step.getWeight(), step.getCodegen(), strategyFqn);
        if (!graph.addEdgeIfAcyclic(edge)) {
            return CommitResult.skipped();
        }
        // Producer-commit on a Path B untyped slot Node: the bridge step is the producer and its
        // output type is the value the slot consumes. Bridges do not carry an Element scope, so the
        // resolver invocation uses the inputNode's underlying scope (its containing method element)
        // as a best-effort anchor; the type-use check on the output type fires first regardless.
        if (frontier.getType().isEmpty()) {
            final var scope = resolveCtx.currentMethod();
            final var nullability =
                    scope == null ? Nullability.UNKNOWN : nullabilityResolver.resolve(step.getOutputType(), scope);
            frontier.setTyping(step.getOutputType(), nullability);
            producerScopes.put(frontier, scope);
            change.markTypeAssigned();
        }
        final var bridgeCodegen = step.getCodegen();
        final var nested = ExpansionGroup.of(
                frontier, List.of(inputNode), bridgeCodegen::render, strategyFqn, Set.of(edge), graph);
        importBoundaryNodes(parentGroup, nested);
        graph.addGroup(nested);
        change.markGroupAdded();
        return CommitResult.committed(allocation.fresh ? inputNode : null);
    }

    private static final class CommitResult {
        final boolean success;
        final @Nullable Node allocated;

        private CommitResult(final boolean success, final @Nullable Node allocated) {
            this.success = success;
            this.allocated = allocated;
        }

        static CommitResult committed(final @Nullable Node allocated) {
            return new CommitResult(true, allocated);
        }

        static CommitResult skipped() {
            return new CommitResult(false, null);
        }
    }

    private void importBoundaryNodes(final ExpansionGroup parent, final ExpansionGroup child) {
        for (final var node : parent.getView().vertexSet()) {
            if (!(node.getLoc() instanceof SourceLocation)) {
                continue;
            }
            if (!child.getView().containsVertex(node)) {
                child.addVertexToView(node);
            }
        }
    }

    private InputAllocation allocateInputNode(
            final MapperGraph graph, final ExpansionGroup parentGroup, final Node frontier, final BridgeStep step) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return allocateForPreserving(graph, parentGroup, frontier, step);
            case ENTERING:
                return allocateForEntering(graph, parentGroup, frontier, step);
            case EXITING:
                return allocateForExiting(graph, frontier, step);
        }
        throw new IllegalStateException("Unknown scope transition: " + step.getScopeTransition());
    }

    private InputAllocation allocateForPreserving(
            final MapperGraph graph, final ExpansionGroup parentGroup, final Node frontier, final BridgeStep step) {
        final var typeMatch = findCandidateByInputType(parentGroup, frontier, step);
        if (typeMatch != null && typeMatch.getLoc().equals(frontier.getLoc())) {
            return new InputAllocation(typeMatch, false);
        }
        return allocateFresh(graph, step.getInputType(), frontier.getLoc(), frontier.getScope(), frontier);
    }

    private InputAllocation allocateForEntering(
            final MapperGraph graph, final ExpansionGroup parentGroup, final Node frontier, final BridgeStep step) {
        final var typeMatch = findCandidateByInputType(parentGroup, frontier, step);
        if (typeMatch != null && !(typeMatch.getLoc() instanceof ElementLocation)) {
            return new InputAllocation(typeMatch, false);
        }
        return allocateFresh(graph, step.getInputType(), frontier.getLoc(), frontier.getScope(), frontier);
    }

    @Nullable
    private Node findCandidateByInputType(final ExpansionGroup group, final Node frontier, final BridgeStep step) {
        return group.getView().vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> !(node.getLoc() instanceof TargetLocation))
                .filter(node -> node.getType().isPresent())
                .filter(node -> resolveCtx.types().isSameType(node.getType().get(), step.getInputType()))
                .findFirst()
                .orElse(null);
    }

    private InputAllocation allocateForExiting(final MapperGraph graph, final Node frontier, final BridgeStep step) {
        final Location elementLoc = new ElementLocation(step.getElementRole());
        return allocateFresh(graph, step.getInputType(), elementLoc, frontier.getScope(), frontier);
    }

    private InputAllocation allocateFresh(
            final MapperGraph graph,
            final TypeMirror type,
            final Location loc,
            final Scope scope,
            final Node frontier) {
        final Optional<Node> parent = loc instanceof ElementLocation ? Optional.of(frontier) : Optional.empty();
        final var fresh = new Node(Optional.of(type), loc, scope, parent);
        graph.addNode(fresh);
        return new InputAllocation(fresh, true);
    }

    private static final class InputAllocation {
        final Node node;
        final boolean fresh;

        InputAllocation(final Node node, final boolean fresh) {
            this.node = node;
            this.fresh = fresh;
        }
    }

    private void registerNestedGroupTarget(
            final Node root,
            final TypeMirror rootType,
            final GroupBuild build,
            final String strategyFqn,
            final MapperGraph graph,
            final ChangeTracker change) {
        // Producer-commit on the parent frontier when this GroupTarget recursion was driven by an
        // expected-type fallback (Path B nested slot). GroupTargets do not carry an Element scope;
        // fall back to the enclosing method element so the resolver still runs at every typing site
        // (mirrors the bridge-step commit pattern below).
        if (root.getType().isEmpty()) {
            final var scope = resolveCtx.currentMethod();
            final var nullability = scope == null ? Nullability.UNKNOWN : nullabilityResolver.resolve(rootType, scope);
            root.setTyping(rootType, nullability);
            producerScopes.put(root, scope);
            change.markTypeAssigned();
        }
        final var slotNodes = new ArrayList<Node>(build.getSlots().size());
        final var slotEdges = new HashSet<Edge>(build.getSlots().size());
        @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
        final IdentityHashMap<Node, io.github.joke.percolate.spi.Slot> slotMetadata =
                new IdentityHashMap<>(build.getSlots().size());
        final var codegen = build.getCodegen();
        for (final var slot : build.getSlots()) {
            final Location slotLoc = new ElementLocation(slot.getName());
            // Path B: nested slot Nodes start untyped; the engine drives candidate search via
            // ExpansionGroup.expectedTypeFor (Slot.type) and types them at their own producer-commit.
            final var slotNode = new Node(Optional.empty(), slotLoc, root.getScope(), Optional.of(root));
            graph.addNode(slotNode);
            slotNodes.add(slotNode);
            slotMetadata.put(slotNode, slot);
            final var edge = Edge.realised(slotNode, root, slot.getWeight(), codegen::render, strategyFqn);
            if (graph.addEdge(edge)) {
                slotEdges.add(edge);
            }
        }
        final var nested = ExpansionGroup.of(root, slotNodes, codegen, strategyFqn, slotEdges, graph, slotMetadata);
        graph.addGroup(nested);
        change.markGroupAdded();
    }

    private static final class StepResult {
        final GroupOutcome.Kind outcome;
        final @Nullable Node failingSlot;

        StepResult(final GroupOutcome.Kind outcome, final @Nullable Node failingSlot) {
            this.outcome = outcome;
            this.failingSlot = failingSlot;
        }

        static StepResult sat() {
            return new StepResult(GroupOutcome.Kind.SAT, null);
        }

        static StepResult pending(final Node slot) {
            return new StepResult(GroupOutcome.Kind.UNSAT_NO_PLAN, slot);
        }

        static StepResult failed(final Node slot) {
            return new StepResult(GroupOutcome.Kind.UNSAT_NO_PLAN, slot);
        }
    }

    private static final class ChangeTracker {
        boolean changed;

        void markSat() {
            changed = true;
        }

        void markGroupAdded() {
            changed = true;
        }

        void markTypeAssigned() {
            changed = true;
        }

        void markEdgeAdded() {
            changed = true;
        }
    }
}
