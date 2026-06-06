package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.spi.AssemblyStrategy;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ElementScope;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Intent;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * Produces a frontier node by running the single {@link ExpansionStrategy} list as one round (no kind-ordering) and
 * branching on each emitted {@link ExpansionStep}'s {@link io.github.joke.percolate.spi.Intent}:
 *
 * <ul>
 *   <li>{@code CONVERSION} folds an edge from an existing in-view candidate (the value being re-typed already
 *       exists) into the <em>current</em> group's view — no fresh node. A round-trip that reuses a node downstream
 *       of the frontier closes an instance cycle the {@link Applier} rejects; no recurrence guard is needed.</li>
 *   <li>{@code BOUNDARY} opens a new sub-group rooted at the frontier with the step's {@code 0..N} slots; container
 *       boundaries carry their {@link ElementScope} onto the realised edge.</li>
 * </ul>
 *
 * <p>Candidates come only from the current group's view — never a global scan — preserving view-scoped, myopic
 * matching. Each accepted step becomes one atomic {@link DeltaBundle}; multi-fire siblings are pruned later by the
 * cost oracle, cycle-rejected siblings by the {@link Applier}.
 */
@SuppressWarnings("PMD.GodClass")
final class FrontierMatcher {

    private final List<ExpansionStrategy> allStrategies;
    private final List<ExpansionStrategy> generalStrategies;
    private final InputAllocator inputAllocator;
    private final ResolveCtx resolveCtx;

    FrontierMatcher(
            final List<ExpansionStrategy> strategies,
            final InputAllocator inputAllocator,
            final ResolveCtx resolveCtx) {
        this.allStrategies = strategies;
        this.generalStrategies = strategies.stream()
                .filter(strategy -> !(strategy instanceof AssemblyStrategy))
                .collect(Collectors.toUnmodifiableList());
        this.inputAllocator = inputAllocator;
        this.resolveCtx = resolveCtx;
    }

    /**
     * Generic production of a typed frontier from in-view candidates. Runs only the <em>general</em> strategies —
     * assembly strategies are excluded here, because firing a constructor on an arbitrary value frontier recurses
     * unboundedly through the reachable type graph; assembly is scoped to {@link #matchAssembly} on assembly roots.
     */
    List<DeltaBundle> matchAt(final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return produce(frontier, group, snapshot, generalStrategies);
    }

    /**
     * Production of an assembly-group root: runs the full strategy list (general + {@link AssemblyStrategy}), so a
     * structured target can be built by a constructor whose slots bind to the root's pre-seeded target leaves, or
     * produced directly (e.g. identity-assigned from a matching source).
     */
    List<DeltaBundle> matchAssembly(final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return produce(frontier, group, snapshot, allStrategies);
    }

    private List<DeltaBundle> produce(
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final List<ExpansionStrategy> strategies) {
        final var targetType = snapshot.effectiveTypeFor(frontier, group);
        if (targetType == null) {
            return List.of();
        }
        final var ctx =
                new FrontierContext(targetType, frontier.getDirective(), candidatesOf(frontier, group, snapshot));
        final var bundles = new ArrayList<DeltaBundle>();
        final var seen = new HashSet<String>();
        final var declaredNames = declaredChildNames(group);
        final var claimedNames = new HashSet<String>();
        for (final var strategy : strategies) {
            final var fqn = strategy.getClass().getName();
            final var assembly = strategy instanceof AssemblyStrategy;
            strategy.expand(ctx, resolveCtx)
                    .filter(step -> accepts(step, targetType, frontier, assembly, declaredNames, seen, fqn))
                    .forEach(step -> toBundle(frontier, step, group, snapshot, fqn, assembly, claimedNames)
                            .ifPresent(bundles::add));
        }
        return bundles;
    }

    /**
     * Whether an emitted step survives the round's guards: it produces the frontier's type, its element-scope fits
     * the frontier, an assembly step's parameter names equal the declared children, and it is not a structural
     * duplicate already seen this round. Ordered so {@code seen} is only marked for an otherwise-acceptable step.
     */
    private boolean accepts(
            final ExpansionStep step,
            final TypeMirror targetType,
            final Node frontier,
            final boolean assembly,
            final Set<String> declaredNames,
            final Set<String> seen,
            final String fqn) {
        return resolveCtx.types().isSameType(step.getOutput(), targetType)
                && scopeMatches(step.getScope(), frontier)
                && (!assembly || assemblyParamsMatchDeclared(step, declaredNames))
                && seen.add(stepSignature(fqn, step));
    }

    /** The declared-child names of an assembly umbrella group: the target-field names a constructor must match. */
    private Set<String> declaredChildNames(final ExpansionGroup group) {
        return group.inputs().stream().map(FrontierMatcher::slotName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Bind-only-declared candidacy: a constructor is a candidate iff its parameter-name set equals the umbrella's
     * declared-child name set. Coverage (no declared child dropped) and no-silent-sourcing (no un-declared parameter
     * invented) are the two halves of this single equality; a no-arg or partial or extra-parameter constructor fails
     * it and never opens a sub-group.
     */
    private static boolean assemblyParamsMatchDeclared(final ExpansionStep step, final Set<String> declaredNames) {
        final var paramNames = step.getInputs().stream().map(Slot::getName).collect(Collectors.toUnmodifiableSet());
        return paramNames.equals(declaredNames);
    }

    /**
     * Source descent: produce the (untyped) root of a path-segment seed group by resolving one path segment past
     * its already-typed slot. The driver feeds the segment as a synthetic single-segment {@link SegmentDirective}
     * and offers the parent (slot) type as the lone candidate; a path-resolver strategy emits a BOUNDARY step typed
     * to the discovered member type. The {@code output == targetType} check is skipped here (the member type is
     * being discovered): a descent step is accepted when it is a one-input BOUNDARY whose input is the parent type
     * and whose output genuinely differs from it (excluding identity folds from non-descent strategies).
     */
    List<DeltaBundle> descend(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var root = group.getRoot();
        final var slot = group.inputs().get(0);
        final var parentType = snapshot.typeOf(slot).orElse(null);
        if (parentType == null) {
            return List.of();
        }
        final var ctx = new FrontierContext(
                parentType,
                Optional.of(new SegmentDirective(appendedSegment(group))),
                List.of(new Candidate(parentType)));
        final var bundles = new ArrayList<DeltaBundle>();
        for (final var strategy : generalStrategies) {
            final var fqn = strategy.getClass().getName();
            strategy.expand(ctx, resolveCtx).forEach(step -> {
                if (isDescentStep(step, parentType)) {
                    bundles.add(descentBundle(root, slot, step, fqn));
                }
            });
        }
        return bundles;
    }

    /**
     * Structural fingerprint of a step within one {@code produce} round, used to drop duplicate emissions. A
     * container's wrap / unwrap-by-synthesis branches are target-driven and ignore their {@code from} type, so
     * {@link io.github.joke.percolate.spi.CombinatorialMatch}'s per-candidate iteration emits the same step once
     * per in-view candidate. Each duplicate would otherwise open a structurally identical, equal-cost twin
     * sub-group that only the plan oracle later prunes. Keyed on the strategy (so genuinely distinct strategies
     * that happen to coincide still both register), the step's intent, scope, output type and input types.
     */
    private static String stepSignature(final String fqn, final ExpansionStep step) {
        final var inputs =
                step.getInputs().stream().map(slot -> slot.getType().toString()).collect(Collectors.joining(","));
        return String.join(
                "|",
                fqn,
                step.getIntent().name(),
                step.getScope().map(Enum::name).orElse(""),
                step.getOutput().toString(),
                inputs);
    }

    private boolean isDescentStep(final ExpansionStep step, final TypeMirror parentType) {
        return step.getIntent() == Intent.BOUNDARY
                && step.getInputs().size() == 1
                && step.getScope().isEmpty()
                && resolveCtx.types().isSameType(step.getInputs().get(0).getType(), parentType)
                && !resolveCtx.types().isSameType(step.getOutput(), parentType);
    }

    private DeltaBundle descentBundle(final Node root, final Node slot, final ExpansionStep step, final String fqn) {
        final var codegen = (EdgeCodegen) step.getCodegen();
        final var edge = Edge.realised(slot, root, step.getWeight(), codegen, fqn, step.getInputs().get(0));
        final var deltas = new ArrayList<Delta>();
        deltas.add(new AddEdge(edge));
        deltas.add(new TypeNode(root, step.getOutput(), descentScope(step)));
        deltas.add(new AddGroup(root, List.of(slot), List.of(), false));
        return new DeltaBundle(fqn, deltas);
    }

    @Nullable
    private static Element descentScope(final ExpansionStep step) {
        final AnnotatedConstruct produced = step.getInputs().get(0).getProducedFrom();
        return produced instanceof Element ? (Element) produced : null;
    }

    private static String appendedSegment(final ExpansionGroup group) {
        final var rootSegs =
                ((SourceLocation) group.getRoot().getLoc()).getPath().getSegments();
        return rootSegs.get(rootSegs.size() - 1);
    }

    private List<Candidate> candidatesOf(
            final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> !(node.getLoc() instanceof TargetLocation))
                .filter(node -> node.getType().isPresent())
                .sorted(Comparator.comparing(Node::id))
                .map(node -> new Candidate(node.getType().orElseThrow()))
                .collect(Collectors.toUnmodifiableList());
    }

    private Optional<DeltaBundle> toBundle(
            final Node frontier,
            final ExpansionStep step,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final String fqn,
            final boolean assembly,
            final Set<String> claimedNames) {
        switch (step.getIntent()) {
            case CONVERSION:
                return convertBundle(frontier, step, group, snapshot, fqn);
            case BOUNDARY:
                return Optional.of(boundaryBundle(frontier, step, group, snapshot, fqn, assembly, claimedNames));
        }
        throw new IllegalStateException("Unknown intent: " + step.getIntent());
    }

    /**
     * Folds a CONVERSION edge into {@code group} by binding the converted value to an existing in-view node of the
     * input type (type-dedup / reuse). The fold edge's endpoints are both already tagged into the group, so the
     * group's derived view shows it without any explicit view mutation. A round-trip that re-derives a type already
     * on the chain reuses its node and closes a cycle the {@link Applier} rejects. When no in-view node of the
     * input type exists no fold is possible (multi-hop conversion synthesis is deferred to the type-conversion
     * change — see project notes); the step is dropped.
     */
    private Optional<DeltaBundle> convertBundle(
            final Node frontier,
            final ExpansionStep step,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final String fqn) {
        final var inputType = step.getInputs().get(0).getType();
        final var input = findInViewByType(inputType, frontier, group, snapshot);
        if (input == null) {
            return Optional.empty();
        }
        final var deltas = new ArrayList<Delta>();
        final var codegen = (EdgeCodegen) step.getCodegen();
        final var edge = Edge.realised(input, frontier, step.getWeight(), codegen, fqn, step.getInputs().get(0));
        deltas.add(new AddEdge(edge));
        if (snapshot.typeOf(frontier).isEmpty()) {
            deltas.add(new TypeNode(frontier, step.getOutput(), snapshot.producerScopeOf(input)));
        }
        return Optional.of(new DeltaBundle(fqn, deltas));
    }

    /** Opens a new sub-group rooted at {@code frontier} with the step's slots; each slot edge carries its Slot. */
    DeltaBundle boundaryBundle(
            final Node frontier,
            final ExpansionStep step,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final String fqn,
            final boolean assembly,
            final Set<String> claimedNames) {
        final var codegen = step.getCodegen();
        final var deltas = new ArrayList<Delta>();
        final var slotNodes = new ArrayList<Node>(step.getInputs().size());
        for (final var spiSlot : step.getInputs()) {
            final var node = assembly
                    ? bindAssemblySlot(spiSlot, group, snapshot, claimedNames, deltas)
                    : bindSlot(spiSlot, frontier, group, step.getScope(), snapshot, deltas);
            slotNodes.add(node);
            // Type a reused-but-untyped slot node with its declared type (replacing pinExpectedTypesOnProducers):
            // a pre-seeded target leaf bound by an assembly / by-name reuse learns the type its directive-binding
            // group must produce toward (effectiveTypeFor now reads node.getType()). Fresh nodes are already typed.
            if (snapshot.typeOf(node).isEmpty()) {
                deltas.add(new TypeNode(node, spiSlot.getType(), slotScope(spiSlot)));
            }
            deltas.add(new AddEdge(realisedEdge(node, frontier, step.getWeight(), codegen, step.getScope(), fqn, spiSlot)));
        }
        if (snapshot.typeOf(frontier).isEmpty()) {
            deltas.add(new TypeNode(frontier, step.getOutput(), producerScopeFor(slotNodes, snapshot)));
        }
        deltas.add(new AddGroup(frontier, slotNodes, boundaryImports(frontier, slotNodes, group, snapshot), false));
        return new DeltaBundle(fqn, deltas);
    }

    @Nullable
    private static Element slotScope(final Slot slot) {
        final AnnotatedConstruct produced = slot.getProducedFrom();
        return produced instanceof Element ? (Element) produced : null;
    }

    /**
     * Binds a boundary slot to a graph node: when producing the group's own root, an existing child slot of the
     * same name (an assembly target leaf) is reused; otherwise a fresh input is allocated by {@link InputAllocator}.
     */
    private Node bindSlot(
            final Slot spiSlot,
            final Node frontier,
            final ExpansionGroup group,
            final Optional<ElementScope> scope,
            final ExpansionSnapshot snapshot,
            final List<Delta> deltas) {
        if (frontier.equals(group.getRoot())) {
            final var existing = existingSlotByName(group, spiSlot.getName());
            if (existing != null) {
                return existing;
            }
        }
        final var allocation = inputAllocator.allocate(spiSlot.getType(), scope, frontier, group, snapshot);
        if (allocation.getAddNode() != null) {
            deltas.add(allocation.getAddNode());
        }
        return allocation.getNode();
    }

    @Nullable
    private Node existingSlotByName(final ExpansionGroup group, final String name) {
        return group.inputs().stream()
                .filter(slot -> name.equals(slotName(slot)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Binds an assembly (constructor) parameter to a per-{@code (name, required-type)} typed leaf. The first
     * constructor to demand a declared child claims the pre-seeded leaf for its type; a later constructor that
     * disagrees on the type (a type-divergent overload) gets its own freshly minted leaf, fed from the very same
     * shared source value by its own directive-binding group. The leaf is never sourced from anything but a
     * directive-declared child — there is no {@link InputAllocator} fall-through here — so an un-declared parameter
     * can never be auto-sourced (the name-set-equality gate in {@code produce} already rejected such constructors).
     */
    private Node bindAssemblySlot(
            final Slot spiSlot,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final Set<String> claimedNames,
            final List<Delta> deltas) {
        final var name = spiSlot.getName();
        final var seeded = existingSlotByName(group, name);
        if (seeded == null) {
            throw new IllegalStateException("assembly parameter has no declared child of the same name: " + name);
        }
        // The first constructor to demand a declared child claims the pre-seeded leaf; every later (OR-sibling)
        // constructor mints its OWN private leaf for the child, even when the type agrees. Sibling constructor
        // groups must keep disjoint slots: PlanView resolves the OR by dropping the losing group's slot edges, and
        // a shared leaf would yield a structurally-equal (value-equal) edge in both groups — dropping the loser's
        // copy would take the winner's with it.
        if (claimedNames.add(name)) {
            return seeded;
        }
        final var source = directiveSourceFor(seeded, snapshot);
        if (source == null) {
            return seeded;
        }
        return mintTypedLeaf(seeded, source, deltas);
    }

    /** The shared source value feeding {@code leaf}, read from the directive-binding seed group rooted at it. */
    @Nullable
    private Node directiveSourceFor(final Node leaf, final ExpansionSnapshot snapshot) {
        return snapshot.groups()
                .filter(GroupShapes::isDirectiveBinding)
                .filter(candidate -> candidate.getRoot().equals(leaf))
                .map(candidate -> candidate.inputs().get(0))
                .findFirst()
                .orElse(null);
    }

    /**
     * Mints a fresh typed leaf at the seeded leaf's location and registers a directive-binding group feeding it from
     * the shared {@code source} (reusing the one source node — never a duplicate). The leaf is left untyped: its
     * directive-binding group pins the declared type via {@code Applier.pinExpectedTypesOnProducers} and produces it
     * (identity direct-assign for an exact match, a widen/box chain for a divergence).
     */
    private Node mintTypedLeaf(final Node seeded, final Node source, final List<Delta> deltas) {
        final var fresh = new Node(Optional.empty(), seeded.getLoc(), seeded.getScope(), seeded.getParent());
        deltas.add(new AddNode(fresh));
        // A SEED bridging edge from the shared source to the fresh leaf, mirroring the seed stage: the minted
        // directive-binding group derives its single input from this edge (a seed group reads its SEED scaffolding
        // edges), and the per-type divergent leaf stays disjoint from the seeded one (design D3 / D7).
        deltas.add(new AddEdge(Edge.seed(source, fresh, Optional.empty(), Optional.empty())));
        deltas.add(new AddGroup(fresh, List.of(source), List.of(), true));
        return fresh;
    }

    /**
     * Conversion-input dedup: the in-view node of {@code inputType}, excluding only the frontier itself. Unlike
     * the boundary {@code InputAllocator} search this does NOT exclude {@link TargetLocation} — re-deriving a type
     * already on the chain (incl. the target root) must reuse that node so a round-trip closes a cycle the
     * {@link Applier} rejects, rather than synthesizing fresh nodes forever (design E1).
     */
    @Nullable
    private Node findInViewByType(
            final TypeMirror inputType,
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> node.getType().isPresent())
                .filter(node -> resolveCtx.types().isSameType(node.getType().get(), inputType))
                .min(Comparator.comparing(Node::id))
                .orElse(null);
    }

    private static Edge realisedEdge(
            final Node from,
            final Node to,
            final int weight,
            final Codegen codegen,
            final Optional<ElementScope> scope,
            final String fqn,
            final Slot consumerSlot) {
        if (codegen instanceof EdgeCodegen && scope.isEmpty()) {
            return Edge.realised(from, to, weight, (EdgeCodegen) codegen, fqn, consumerSlot);
        }
        return Edge.realised(from, to, weight, codegen, scope.orElseThrow(), fqn, consumerSlot);
    }

    private static boolean scopeMatches(final Optional<ElementScope> scope, final Node frontier) {
        if (scope.isEmpty()) {
            return true;
        }
        return scope.get() == ElementScope.ENTERING
                ? frontier.getLoc() instanceof ElementLocation
                : !(frontier.getLoc() instanceof ElementLocation);
    }

    @Nullable
    private static Element producerScopeFor(final List<Node> slotNodes, final ExpansionSnapshot snapshot) {
        return slotNodes.stream()
                .map(snapshot::producerScopeOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<Node> boundaryImports(
            final Node frontier,
            final List<Node> slotNodes,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> node.getLoc() instanceof SourceLocation)
                .filter(node -> !node.equals(frontier) && !slotNodes.contains(node))
                .sorted(Comparator.comparing(Node::id))
                .collect(Collectors.toUnmodifiableList());
    }

    private static String slotName(final Node slot) {
        if (slot.getLoc() instanceof TargetLocation) {
            final var segments = ((TargetLocation) slot.getLoc()).getPath().getSegments();
            return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
        }
        if (slot.getLoc() instanceof SourceLocation) {
            final var segments = ((SourceLocation) slot.getLoc()).getPath().getSegments();
            return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
        }
        if (slot.getLoc() instanceof ElementLocation) {
            return ((ElementLocation) slot.getLoc()).getRole();
        }
        return "";
    }
}
