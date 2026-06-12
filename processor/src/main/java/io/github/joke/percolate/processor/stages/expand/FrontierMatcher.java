package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

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
import java.util.stream.Stream;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
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
                .collect(toUnmodifiableList());
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

    /**
     * Produces a constant-binding root: runs the general strategies on the constant-value slot offered as a frontier
     * typed to the demanded (root) target type, keeps the zero-input {@code BOUNDARY} terminal producer
     * ({@code ConstantValue}), types the constant node to that type, and realises the literal producer edge from the
     * constant node into the root. Emits nothing until the root's declared type is known (it is pinned by the
     * consuming assembly), and nothing when the literal cannot be coerced — leaving the demand UNSAT for the late
     * coercion-failure diagnostic. The constant node carries the {@code @Map} {@link io.github.joke.percolate.spi.Directive}
     * (stamped at seed time) so {@code ConstantValue} reads its {@code constant}.
     */
    List<DeltaBundle> produceConstant(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var constNode = group.inputs().get(0);
        final var root = group.getRoot();
        final var targetType = snapshot.effectiveTypeFor(root);
        if (targetType == null) {
            return List.of();
        }
        final var ctx = new FrontierContext(targetType, constNode.getDirective(), List.of());
        final var alreadyTyped = snapshot.typeOf(constNode).isPresent();
        return generalStrategies.stream()
                .flatMap(strategy -> strategy.expand(ctx, resolveCtx)
                        .filter(step -> isConstantStep(step, targetType))
                        .map(step -> constantBundle(
                                constNode,
                                root,
                                step,
                                alreadyTyped,
                                strategy.getClass().getName())))
                .collect(toUnmodifiableList());
    }

    private boolean isConstantStep(final ExpansionStep step, final TypeMirror targetType) {
        return step.getIntent() == Intent.BOUNDARY
                && step.getInputs().isEmpty()
                && resolveCtx.types().isSameType(step.getOutput(), targetType);
    }

    private static DeltaBundle constantBundle(
            final Node constNode,
            final Node root,
            final ExpansionStep step,
            final boolean alreadyTyped,
            final String fqn) {
        final var edge = Edge.realised(step.getWeight(), (EdgeCodegen) step.getCodegen(), fqn);
        final var deltas = new ArrayList<Delta>();
        if (!alreadyTyped) {
            // Scope is null: the engine stamps a constant-value node NON_NULL by its ConstantLocation (design D6),
            // never via the resolver.
            deltas.add(new TypeNode(constNode, step.getOutput(), null));
        }
        deltas.add(new AddEdge(constNode, root, edge));
        return new DeltaBundle(fqn, deltas);
    }

    private List<DeltaBundle> produce(
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final List<ExpansionStrategy> strategies) {
        return Optional.ofNullable(snapshot.effectiveTypeFor(frontier))
                .map(targetType ->
                        new Round(frontier, group, snapshot, targetType, declaredChildNames(group)).produce(strategies))
                .orElseGet(List::of);
    }

    /** The declared-child names of an assembly umbrella group: the target-field names a constructor must match. */
    private static Set<String> declaredChildNames(final ExpansionGroup group) {
        return group.inputs().stream().map(slot -> slot.getLoc().slotName()).collect(toUnmodifiableSet());
    }

    /**
     * Bind-only-declared candidacy: a constructor is a candidate iff its parameter-name set equals the umbrella's
     * declared-child name set. Coverage (no declared child dropped) and no-silent-sourcing (no un-declared parameter
     * invented) are the two halves of this single equality; a no-arg or partial or extra-parameter constructor fails
     * it and never opens a sub-group.
     */
    private static boolean assemblyParamsMatchDeclared(final ExpansionStep step, final Set<String> declaredNames) {
        final var paramNames = step.getInputs().stream().map(Slot::getName).collect(toUnmodifiableSet());
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
        final var slot = group.inputs().get(0);
        return snapshot.typeOf(slot)
                .map(parentType -> descentBundles(group, slot, parentType))
                .orElseGet(List::of);
    }

    private List<DeltaBundle> descentBundles(final ExpansionGroup group, final Node slot, final TypeMirror parentType) {
        final var ctx = new FrontierContext(
                parentType,
                Optional.of(new SegmentDirective(appendedSegment(group))),
                List.of(new Candidate(parentType)));
        return generalStrategies.stream()
                .flatMap(strategy -> strategy.expand(ctx, resolveCtx)
                        .filter(step -> isDescentStep(step, parentType))
                        .map(step -> descentBundle(
                                group.getRoot(), slot, step, strategy.getClass().getName())))
                .collect(toUnmodifiableList());
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
        final var input = step.getInputs().get(0);
        final var edge = Edge.realised(step.getWeight(), (EdgeCodegen) step.getCodegen(), fqn, input);
        return new DeltaBundle(
                fqn,
                List.of(
                        new AddEdge(slot, root, edge),
                        new TypeNode(root, step.getOutput(), producedElement(input)),
                        new AddGroup(root, List.of(slot), List.of(), false)));
    }

    /** The {@link Element} a slot's value is produced from, when its {@code producedFrom} is one. */
    @Nullable
    private static Element producedElement(final Slot slot) {
        final AnnotatedConstruct produced = slot.getProducedFrom();
        return produced instanceof Element ? (Element) produced : null;
    }

    private static String appendedSegment(final ExpansionGroup group) {
        return group.getRoot().getLoc().slotName();
    }

    /** Mints a realised edge, attaching the container {@link ElementScope} when the step carries one. */
    private static Edge realisedEdge(
            final int weight,
            final Codegen codegen,
            final Optional<ElementScope> scope,
            final String fqn,
            final Slot consumerSlot) {
        if (codegen instanceof EdgeCodegen && scope.isEmpty()) {
            return Edge.realised(weight, (EdgeCodegen) codegen, fqn, consumerSlot);
        }
        return Edge.realised(weight, codegen, scope.orElseThrow(), fqn, consumerSlot);
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

    /**
     * Mints a fresh typed leaf at the seeded leaf's location and registers a directive-binding group feeding it from
     * the shared {@code source} (reusing the one source node — never a duplicate). The leaf is left untyped: its
     * directive-binding group pins the declared type via {@code Applier.pinExpectedTypesOnProducers} and produces it
     * (identity direct-assign for an exact match, a widen/box chain for a divergence).
     */
    private static Node mintTypedLeaf(final Node seeded, final Node source, final List<Delta> deltas) {
        final var fresh = new Node(Optional.empty(), seeded.getLoc(), seeded.getScope(), seeded.getParent());
        deltas.add(new AddNode(fresh));
        // A SEED bridging edge from the shared source to the fresh leaf, mirroring the seed stage: the minted
        // directive-binding group derives its single input from this edge (a seed group reads its SEED scaffolding
        // edges), and the per-type divergent leaf stays disjoint from the seeded one (design D3 / D7).
        deltas.add(new AddEdge(source, fresh, Edge.seed(Optional.empty())));
        deltas.add(new AddGroup(fresh, List.of(source), List.of(), true));
        return fresh;
    }

    /**
     * One production round at a single frontier: holds the per-round state (the frontier, its group and snapshot,
     * the demanded type, the declared-child names, and the round's dedup / claim sets) so each step decision reads
     * it without parameter threading.
     */
    @RequiredArgsConstructor
    private final class Round {

        private final Node frontier;
        private final ExpansionGroup group;
        private final ExpansionSnapshot snapshot;
        private final TypeMirror targetType;
        private final Set<String> declaredNames;
        private final Set<String> seen = new HashSet<>();
        private final Set<String> claimedNames = new HashSet<>();

        List<DeltaBundle> produce(final List<ExpansionStrategy> strategies) {
            final var ctx = new FrontierContext(targetType, frontier.getDirective(), candidates());
            return strategies.stream()
                    .flatMap(strategy -> bundlesFrom(strategy, ctx))
                    .collect(toUnmodifiableList());
        }

        private Stream<DeltaBundle> bundlesFrom(final ExpansionStrategy strategy, final FrontierContext ctx) {
            final var fqn = strategy.getClass().getName();
            final var assembly = strategy instanceof AssemblyStrategy;
            return strategy.expand(ctx, resolveCtx)
                    .filter(step -> accepts(step, assembly, fqn))
                    .map(step -> toBundle(step, fqn, assembly));
        }

        /**
         * Whether an emitted step survives the round's guards: it produces the frontier's type, its element-scope
         * fits the frontier, an assembly step's parameter names equal the declared children, and it is not a
         * structural duplicate already seen this round. Ordered so {@code seen} is only marked for an
         * otherwise-acceptable step.
         */
        private boolean accepts(final ExpansionStep step, final boolean assembly, final String fqn) {
            return resolveCtx.types().isSameType(step.getOutput(), targetType)
                    && scopeMatches(step.getScope(), frontier)
                    && (!assembly || assemblyParamsMatchDeclared(step, declaredNames))
                    && seen.add(stepSignature(fqn, step));
        }

        private List<Candidate> candidates() {
            return snapshot.viewOf(group).vertexSet().stream()
                    .filter(node -> !node.equals(frontier))
                    .filter(node -> !(node.getLoc() instanceof TargetLocation))
                    .filter(node -> node.getType().isPresent())
                    .sorted(Comparator.comparing(Node::id))
                    .map(node -> new Candidate(node.getType().orElseThrow()))
                    .collect(toUnmodifiableList());
        }

        private DeltaBundle toBundle(final ExpansionStep step, final String fqn, final boolean assembly) {
            switch (step.getIntent()) {
                case CONVERSION:
                    return convertBundle(step, fqn);
                case BOUNDARY:
                    return boundaryBundle(step, fqn, assembly);
            }
            throw new IllegalStateException("Unknown intent: " + step.getIntent());
        }

        /**
         * Folds a CONVERSION edge into {@code group}, re-using an in-view node of the input type when one exists
         * (type-dedup) or synthesizing a fresh type-keyed conversion intermediate when none does. The fold edge's
         * endpoints are both tagged into the group (the reused node already is; a synthesized one is tagged as it is
         * added), so the group's derived view shows the edge without any explicit view mutation, and a later pass
         * expands the synthesized intermediate's own producers (design E1/E2). A synthesized intermediate never
         * pollutes the group's demand {@code inputs()}: a seed group derives inputs from its SEED edges, and a
         * sub-group from its slot edges into the root — neither is the synthesized node's REALISED fold edge. A
         * round-trip that re-derives a type already on the chain reuses its node and closes a cycle the
         * {@link Applier} rejects.
         */
        private DeltaBundle convertBundle(final ExpansionStep step, final String fqn) {
            final var consumerSlot = step.getInputs().get(0);
            final var deltas = new ArrayList<Delta>();
            final var input = findInViewByType(consumerSlot.getType())
                    .orElseGet(() -> synthesizeInput(consumerSlot.getType(), deltas));
            deltas.add(new AddEdge(
                    input,
                    frontier,
                    Edge.realised(step.getWeight(), (EdgeCodegen) step.getCodegen(), fqn, consumerSlot)));
            if (snapshot.typeOf(frontier).isEmpty()) {
                deltas.add(new TypeNode(frontier, step.getOutput(), snapshot.producerScopeOf(input)));
            }
            return new DeltaBundle(fqn, deltas);
        }

        /** Synthesizes a fresh conversion intermediate at the frontier's location, tagged into the group's view. */
        private Node synthesizeInput(final TypeMirror inputType, final List<Delta> deltas) {
            final var synthesized =
                    new Node(Optional.of(inputType), frontier.getLoc(), frontier.getScope(), frontier.getParent());
            deltas.add(new AddNode(synthesized, frontier.getDirective().orElse(null), group.getId()));
            return synthesized;
        }

        /**
         * Conversion-input dedup: the in-view node of {@code inputType}, excluding only the frontier itself. Unlike
         * the boundary {@code InputAllocator} search this does NOT exclude {@link TargetLocation} — re-deriving a
         * type already on the chain (incl. the target root) must reuse that node so a round-trip closes a cycle the
         * {@link Applier} rejects, rather than synthesizing fresh nodes forever (design E1).
         */
        private Optional<Node> findInViewByType(final TypeMirror inputType) {
            return snapshot.viewOf(group).vertexSet().stream()
                    .filter(node -> !node.equals(frontier))
                    .filter(node -> node.getType().isPresent())
                    .filter(node -> resolveCtx.types().isSameType(node.getType().orElseThrow(), inputType))
                    .min(Comparator.comparing(Node::id));
        }

        /** Opens a new sub-group rooted at the frontier with the step's slots; each slot edge carries its Slot. */
        private DeltaBundle boundaryBundle(final ExpansionStep step, final String fqn, final boolean assembly) {
            final var deltas = new ArrayList<Delta>();
            final var slotNodes = step.getInputs().stream()
                    .map(spiSlot -> bindAndWireSlot(step, spiSlot, fqn, assembly, deltas))
                    .collect(toUnmodifiableList());
            if (snapshot.typeOf(frontier).isEmpty()) {
                deltas.add(new TypeNode(frontier, step.getOutput(), producerScopeFor(slotNodes, snapshot)));
            }
            deltas.add(new AddGroup(frontier, slotNodes, boundaryImports(slotNodes), false));
            return new DeltaBundle(fqn, deltas);
        }

        private Node bindAndWireSlot(
                final ExpansionStep step,
                final Slot spiSlot,
                final String fqn,
                final boolean assembly,
                final List<Delta> deltas) {
            final var node = assembly ? bindAssemblySlot(spiSlot, deltas) : bindSlot(spiSlot, step.getScope(), deltas);
            // Type a reused-but-untyped slot node with its declared type (replacing pinExpectedTypesOnProducers):
            // a pre-seeded target leaf bound by an assembly / by-name reuse learns the type its directive-binding
            // group must produce toward (effectiveTypeFor now reads node.getType()). Fresh nodes are already typed.
            if (snapshot.typeOf(node).isEmpty()) {
                deltas.add(new TypeNode(node, spiSlot.getType(), producedElement(spiSlot)));
            }
            deltas.add(new AddEdge(
                    node, frontier, realisedEdge(step.getWeight(), step.getCodegen(), step.getScope(), fqn, spiSlot)));
            return node;
        }

        /**
         * Binds a boundary slot to a graph node: when producing the group's own root, an existing child slot of the
         * same name (an assembly target leaf) is reused; otherwise a fresh input is allocated by
         * {@link InputAllocator}.
         */
        private Node bindSlot(final Slot spiSlot, final Optional<ElementScope> scope, final List<Delta> deltas) {
            return reusableRootSlot(spiSlot.getName()).orElseGet(() -> allocateSlot(spiSlot, scope, deltas));
        }

        private Optional<Node> reusableRootSlot(final String name) {
            return frontier.equals(group.getRoot()) ? existingSlotByName(name) : Optional.empty();
        }

        private Node allocateSlot(final Slot spiSlot, final Optional<ElementScope> scope, final List<Delta> deltas) {
            final var allocation = inputAllocator.allocate(spiSlot.getType(), scope, frontier, group, snapshot);
            Optional.ofNullable(allocation.getAddNode()).ifPresent(deltas::add);
            return allocation.getNode();
        }

        private Optional<Node> existingSlotByName(final String name) {
            return group.inputs().stream()
                    .filter(slot -> name.equals(slot.getLoc().slotName()))
                    .findFirst();
        }

        /**
         * Binds an assembly (constructor) parameter to a per-{@code (name, required-type)} typed leaf. The first
         * constructor to demand a declared child claims the pre-seeded leaf for its type; a later constructor that
         * disagrees on the type (a type-divergent overload) gets its own freshly minted leaf, fed from the very same
         * shared source value by its own directive-binding group. The leaf is never sourced from anything but a
         * directive-declared child — there is no {@link InputAllocator} fall-through here — so an un-declared
         * parameter can never be auto-sourced (the name-set-equality gate in {@code produce} already rejected such
         * constructors).
         */
        private Node bindAssemblySlot(final Slot spiSlot, final List<Delta> deltas) {
            final var name = spiSlot.getName();
            final var seeded = existingSlotByName(name)
                    .orElseThrow(() -> new IllegalStateException(
                            "assembly parameter has no declared child of the same name: " + name));
            // The first constructor to demand a declared child claims the pre-seeded leaf; every later (OR-sibling)
            // constructor mints its OWN private leaf for the child, even when the type agrees. Sibling constructor
            // groups must keep disjoint slots: PlanView resolves the OR by dropping the losing group's slot edges,
            // and a shared leaf would yield a structurally-equal (value-equal) edge in both groups — dropping the
            // loser's copy would take the winner's with it.
            if (claimedNames.add(name)) {
                return seeded;
            }
            return directiveSourceFor(seeded)
                    .map(source -> mintTypedLeaf(seeded, source, deltas))
                    .orElse(seeded);
        }

        /** The shared source value feeding {@code leaf}, read from the directive-binding seed group rooted at it. */
        private Optional<Node> directiveSourceFor(final Node leaf) {
            return snapshot.groups()
                    .filter(GroupShapes::isDirectiveBinding)
                    .filter(candidate -> candidate.getRoot().equals(leaf))
                    .map(candidate -> candidate.inputs().get(0))
                    .findFirst();
        }

        private List<Node> boundaryImports(final List<Node> slotNodes) {
            return snapshot.viewOf(group).vertexSet().stream()
                    .filter(node -> node.getLoc() instanceof SourceLocation)
                    .filter(node -> !node.equals(frontier) && !slotNodes.contains(node))
                    .sorted(Comparator.comparing(Node::id))
                    .collect(toUnmodifiableList());
        }
    }
}
