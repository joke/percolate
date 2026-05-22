package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupOutcome;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.GroupBuild;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeTransition;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ExpandGroupsPhase implements ExpansionPhase {

    static final int MAX_SLOT_ROUNDS = 64;
    static final int MAX_WORK_LIST_ITERATIONS = 256;

    private final List<Bridge> bridges;
    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        final Deque<ExpansionGroup> workList = new ArrayDeque<>();
        graph.groups().forEach(workList::add);
        drainWorkList(graph, workList);
    }

    private void drainWorkList(final MapperGraph graph, final Deque<ExpansionGroup> workList) {
        var iterations = 0;
        while (!workList.isEmpty()) {
            if (iterations >= MAX_WORK_LIST_ITERATIONS) {
                recordDidNotConverge(graph, workList.removeFirst());
            } else {
                fillGroup(workList.removeFirst(), graph, workList);
            }
            iterations++;
        }
    }

    private void recordDidNotConverge(final MapperGraph graph, final ExpansionGroup unprocessed) {
        final var failingSlot = unprocessed.getSlots().isEmpty()
                ? unprocessed.getRoot()
                : unprocessed.getSlots().get(0);
        graph.recordGroupOutcome(GroupOutcome.unsatDidNotConverge(unprocessed, failingSlot));
    }

    private void fillGroup(final ExpansionGroup group, final MapperGraph graph, final Deque<ExpansionGroup> workList) {
        final var sourceRoots = SourceReachability.sourceParameterRoots(graph);
        for (final var slot : group.getSlots()) {
            final var slotOutcome = resolveSlot(slot, graph, sourceRoots, workList);
            if (slotOutcome != SlotOutcome.SAT) {
                graph.recordGroupOutcome(
                        slotOutcome == SlotOutcome.UNSAT_NO_PLAN
                                ? GroupOutcome.unsatNoPlan(group, slot)
                                : GroupOutcome.unsatDidNotConverge(group, slot));
                return;
            }
        }
        // A group SATs iff its root is also producible. For groups whose initialEdges contain
        // slot→root REALISED edges (ConstructorCall, DirectiveBinding, etc.), this is trivially
        // true once the slots SAT — resolveSlot returns immediately because the root is already
        // reachable.
        final var rootOutcome = resolveSlot(group.getRoot(), graph, sourceRoots, workList);
        if (rootOutcome != SlotOutcome.SAT) {
            graph.recordGroupOutcome(
                    rootOutcome == SlotOutcome.UNSAT_NO_PLAN
                            ? GroupOutcome.unsatNoPlan(group, group.getRoot())
                            : GroupOutcome.unsatDidNotConverge(group, group.getRoot()));
            return;
        }
        graph.recordGroupOutcome(GroupOutcome.sat(group));
    }

    private enum SlotOutcome {
        SAT,
        UNSAT_NO_PLAN,
        UNSAT_DID_NOT_CONVERGE
    }

    private SlotOutcome resolveSlot(
            final Node slot,
            final MapperGraph graph,
            final Set<Node> sourceRoots,
            final Deque<ExpansionGroup> workList) {
        if (SourceReachability.slotReachable(slot, graph, sourceRoots)) {
            return SlotOutcome.SAT;
        }
        var frontier = List.of(slot);
        for (var round = 1; round <= MAX_SLOT_ROUNDS; round++) {
            final var newNodes = new ArrayList<Node>();
            for (final var f : frontier) {
                expandFrontier(f, graph, workList, newNodes);
            }
            if (SourceReachability.slotReachable(slot, graph, sourceRoots)) {
                return SlotOutcome.SAT;
            }
            if (newNodes.isEmpty()) {
                return SlotOutcome.UNSAT_NO_PLAN;
            }
            frontier = newNodes;
        }
        return SlotOutcome.UNSAT_DID_NOT_CONVERGE;
    }

    private void expandFrontier(
            final Node frontierNode,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList,
            final List<Node> newNodes) {
        final var frontierType = frontierNode.getType().orElse(null);
        if (frontierType == null) {
            return;
        }
        final var candidates = SourceReachability.candidateInputs(frontierNode.getScope(), graph).stream()
                .filter(n -> !n.equals(frontierNode))
                .collect(Collectors.toUnmodifiableList());
        if (tryBridges(frontierNode, frontierType, candidates, graph, workList, newNodes)) {
            return;
        }
        tryGroupTargets(frontierNode, frontierType, graph, workList);
    }

    private boolean tryBridges(
            final Node frontierNode,
            final TypeMirror frontierType,
            final List<Node> candidates,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList,
            final List<Node> newNodes) {
        var anyMatched = false;
        for (final var bridge : bridges) {
            for (final var candidate : candidates) {
                final var match =
                        tryBridgeOnCandidate(bridge, candidate, frontierNode, frontierType, graph, workList);
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
            final Node frontierNode,
            final TypeMirror frontierType,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        final var candidateType = candidate.getType().orElse(null);
        if (candidateType == null) {
            return BridgeMatch.noMatch();
        }
        final var steps = bridge.bridge(candidateType, frontierType, resolveCtx).collect(Collectors.toList());
        for (final var step : steps) {
            if (!resolveCtx.types().isSameType(step.getOutputType(), frontierType)) {
                continue;
            }
            if (!stepMatchesFrontierScope(step, frontierNode)) {
                continue;
            }
            final var allocated =
                    commitBridgeStep(graph, workList, frontierNode, candidate, step, bridge.getClass().getName());
            return new BridgeMatch(true, allocated);
        }
        return BridgeMatch.noMatch();
    }

    private static boolean stepMatchesFrontierScope(final BridgeStep step, final Node frontierNode) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return true;
            case ENTERING:
                return frontierNode.getLoc() instanceof ElementLocation;
            case EXITING:
                return !(frontierNode.getLoc() instanceof ElementLocation);
            default:
                return false;
        }
    }

    private boolean tryGroupTargets(
            final Node frontierNode,
            final TypeMirror frontierType,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        for (final var groupTarget : groupTargets) {
            final var build = groupTarget.buildFor(frontierType, List.of(), resolveCtx);
            if (build.isPresent()) {
                registerNestedGroup(
                        frontierNode, build.get(), groupTarget.getClass().getName(), graph, workList);
                return true;
            }
        }
        return false;
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

    private @Nullable Node commitBridgeStep(
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList,
            final Node frontierNode,
            final Node candidate,
            final BridgeStep step,
            final String strategyFqn) {
        final var allocation = allocateOrReuseInputNode(graph, frontierNode, candidate, step);
        final var inputNode = allocation.node;
        final var fresh = allocation.fresh ? inputNode : null;
        if (inputNode.equals(frontierNode)) {
            return fresh;
        }
        final var edge = Edge.realised(
                inputNode, frontierNode, step.getWeight(), step.getCodegen(), strategyFqn);
        graph.addEdge(edge);
        if (step.getScopeTransition() != ScopeTransition.PRESERVING) {
            final var bridgeCodegen = step.getCodegen();
            final var nested = ExpansionGroup.of(
                    frontierNode,
                    List.of(inputNode),
                    bridgeCodegen::render,
                    strategyFqn,
                    Set.of(edge),
                    graph);
            graph.addGroup(nested);
            workList.add(nested);
        }
        return fresh;
    }

    private InputAllocation allocateOrReuseInputNode(
            final MapperGraph graph, final Node frontierNode, final Node candidate, final BridgeStep step) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return allocateForPreserving(graph, frontierNode, candidate, step);
            case ENTERING:
                return allocateForEntering(graph, frontierNode, candidate, step);
            case EXITING:
                return allocateForExiting(graph, frontierNode, candidate, step);
            default:
                throw new IllegalStateException("Unknown scope transition: " + step.getScopeTransition());
        }
    }

    private InputAllocation allocateForPreserving(
            final MapperGraph graph, final Node frontierNode, final Node candidate, final BridgeStep step) {
        if (candidateTypeMatches(candidate, step.getInputType())) {
            return new InputAllocation(candidate, false);
        }
        return allocateFresh(graph, step.getInputType(), frontierNode.getLoc(), frontierNode.getScope());
    }

    private InputAllocation allocateForEntering(
            final MapperGraph graph, final Node frontierNode, final Node candidate, final BridgeStep step) {
        final var sameElement = findExistingNode(
                graph, frontierNode.getScope(), frontierNode.getLoc(), step.getInputType(), frontierNode);
        if (sameElement.isPresent()) {
            return new InputAllocation(sameElement.get(), false);
        }
        if (candidateTypeMatches(candidate, step.getInputType())
                && !(candidate.getLoc() instanceof ElementLocation)) {
            return new InputAllocation(candidate, false);
        }
        final var sameType = resolveCtx.types().isSameType(step.getInputType(), step.getOutputType());
        final var freshLoc = sameType ? candidate.getLoc() : frontierNode.getLoc();
        return allocateFresh(graph, step.getInputType(), freshLoc, frontierNode.getScope());
    }

    private InputAllocation allocateForExiting(
            final MapperGraph graph, final Node frontierNode, final Node candidate, final BridgeStep step) {
        final Location elementLoc = new ElementLocation(step.getElementRole());
        if (candidateTypeMatches(candidate, step.getInputType()) && candidate.getLoc().equals(elementLoc)) {
            return new InputAllocation(candidate, false);
        }
        final var existing = findExistingNode(graph, frontierNode.getScope(), elementLoc, step.getInputType(), null);
        if (existing.isPresent()) {
            return new InputAllocation(existing.get(), false);
        }
        return allocateFresh(graph, step.getInputType(), elementLoc, frontierNode.getScope());
    }

    private boolean candidateTypeMatches(final Node candidate, final TypeMirror inputType) {
        return candidate.getType().isPresent()
                && resolveCtx.types().isSameType(inputType, candidate.getType().get());
    }

    private Optional<Node> findExistingNode(
            final MapperGraph graph,
            final Scope scope,
            final Location loc,
            final TypeMirror type,
            final @Nullable Node exclude) {
        return graph.nodes()
                .filter(n -> exclude == null || !n.equals(exclude))
                .filter(n -> n.getScope().equals(scope))
                .filter(n -> n.getLoc().equals(loc))
                .filter(n -> n.getType().isPresent()
                        && resolveCtx.types().isSameType(n.getType().get(), type))
                .findFirst();
    }

    private InputAllocation allocateFresh(
            final MapperGraph graph, final TypeMirror type, final Location loc, final Scope scope) {
        final var fresh = new Node(Optional.of(type), loc, scope);
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

    private void registerNestedGroup(
            final Node root,
            final GroupBuild build,
            final String strategyFqn,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        final var slotNodes = new ArrayList<Node>(build.getSlots().size());
        final var slotEdges = new HashSet<Edge>(build.getSlots().size());
        final var codegen = build.getCodegen();
        for (final var slot : build.getSlots()) {
            final Location slotLoc = new ElementLocation(slot.getName());
            final var slotNode = new Node(Optional.of(slot.getType()), slotLoc, root.getScope());
            graph.addNode(slotNode);
            slotNodes.add(slotNode);
            final var edge = Edge.realised(slotNode, root, slot.getWeight(), codegen::render, strategyFqn);
            graph.addEdge(edge);
            slotEdges.add(edge);
        }
        final var nested = ExpansionGroup.of(root, slotNodes, codegen, strategyFqn, slotEdges, graph);
        graph.addGroup(nested);
        workList.add(nested);
    }
}
