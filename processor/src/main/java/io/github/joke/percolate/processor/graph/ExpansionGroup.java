package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.GroupCodegen;
import io.github.joke.percolate.spi.Slot;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import org.jgrapht.graph.AsSubgraph;
import org.jspecify.annotations.Nullable;

@Getter
public final class ExpansionGroup {

    private final Node root;
    private final List<Node> slots;
    private final GroupCodegen codegen;
    private final String strategyClassFqn;
    private final AsSubgraph<Node, Edge> view;
    private final MapperGraph parent;
    private final Map<Node, Slot> slotMetadata;

    /**
     * Synthesized {@code CONVERSION} input nodes registered into this group during expansion (design E2). They are
     * expanded as frontiers (their own producers discovered) but are NOT AND-required for group SAT — an
     * unreachable one is a retained dead end. Insertion-ordered for deterministic expansion.
     */
    @Getter(lombok.AccessLevel.NONE)
    private final Set<Node> conversionFrontiers = new LinkedHashSet<>();

    private ExpansionGroup(
            final Node root,
            final List<Node> slots,
            final GroupCodegen codegen,
            final String strategyClassFqn,
            final AsSubgraph<Node, Edge> view,
            final MapperGraph parent,
            final Map<Node, Slot> slotMetadata) {
        this.root = root;
        this.slots = slots;
        this.codegen = codegen;
        this.strategyClassFqn = strategyClassFqn;
        this.view = view;
        this.parent = parent;
        this.slotMetadata = slotMetadata;
    }

    public static ExpansionGroup of(
            final Node root,
            final List<Node> slots,
            final GroupCodegen codegen,
            final String strategyClassFqn,
            final Set<Edge> initialEdges,
            final MapperGraph parent) {
        return of(root, slots, codegen, strategyClassFqn, initialEdges, parent, Map.of());
    }

    public static ExpansionGroup of(
            final Node root,
            final List<Node> slots,
            final GroupCodegen codegen,
            final String strategyClassFqn,
            final Set<Edge> initialEdges,
            final MapperGraph parent,
            final Map<Node, Slot> slotMetadata) {
        final var underlying = parent.underlyingGraph();
        validateMembership(underlying, root, slots, initialEdges);
        final var vertices = new HashSet<Node>(slots.size() + 1);
        vertices.add(root);
        vertices.addAll(slots);
        final var view = new AsSubgraph<>(underlying, vertices, new HashSet<>(initialEdges));
        @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
        final IdentityHashMap<Node, Slot> metaCopy = new IdentityHashMap<>(slotMetadata.size());
        for (final var entry : slotMetadata.entrySet()) {
            metaCopy.put(entry.getKey(), entry.getValue());
        }
        return new ExpansionGroup(root, List.copyOf(slots), codegen, strategyClassFqn, view, parent, metaCopy);
    }

    /**
     * The consumer-side {@link AnnotatedConstruct} (typically a constructor parameter or setter parameter
     * {@link javax.lang.model.element.VariableElement}) that this slot represents. Used by code-generation
     * to derive the consumer's nullability contract on demand. Returns {@code null} for slots that have
     * no consumer metadata recorded (e.g., directive-binding source-leaf slots).
     */
    public @Nullable AnnotatedConstruct consumerContractFor(final Node slot) {
        final var meta = slotMetadata.get(slot);
        return meta == null ? null : meta.getProducedFrom();
    }

    /**
     * The consumer-expected {@link TypeMirror} for this slot. Drives candidate search during expansion
     * for slot Nodes that are created untyped (see "Slot Nodes are typed at producer commit"). Returns
     * {@code null} for slots that have no recorded expected type.
     */
    public @Nullable TypeMirror expectedTypeFor(final Node slot) {
        final var meta = slotMetadata.get(slot);
        return meta == null ? null : meta.getType();
    }

    /**
     * Records the consumer-expected {@link Slot} (declared target type) for a node already present in this
     * group. Used by target-chain scaffolding to pin the declared type onto the directive-binding group that
     * shares the node as its root, so the directive-binding expander can read it via {@link #expectedTypeFor}
     * without a cross-group scan. Scaffolding-only: call before the expansion driver runs.
     */
    public void recordExpectedType(final Node node, final Slot slot) {
        slotMetadata.put(node, slot);
    }

    private static void validateMembership(
            final org.jgrapht.Graph<Node, Edge> underlying,
            final Node root,
            final List<Node> slots,
            final Set<Edge> initialEdges) {
        if (!underlying.containsVertex(root)) {
            throw new IllegalArgumentException("ExpansionGroup root is not a vertex of the parent graph");
        }
        for (final var slot : slots) {
            if (!underlying.containsVertex(slot)) {
                throw new IllegalArgumentException("ExpansionGroup slot is not a vertex of the parent graph");
            }
        }
        for (final var edge : initialEdges) {
            validateInitialEdge(underlying, edge);
        }
    }

    private static void validateInitialEdge(final org.jgrapht.Graph<Node, Edge> underlying, final Edge edge) {
        if (!underlying.containsEdge(edge)) {
            throw new IllegalArgumentException("ExpansionGroup initial edge is not present in the parent graph");
        }
        if (edge.getKind() != EdgeKind.REALISED) {
            throw new IllegalArgumentException("ExpansionGroup initial edge must be REALISED");
        }
    }

    public boolean contains(final Edge edge) {
        return view.containsEdge(edge);
    }

    public void addVertexToView(final Node node) {
        if (!parent.underlyingGraph().containsVertex(node)) {
            throw new IllegalArgumentException("addVertexToView: node is not a member of the parent graph");
        }
        view.addVertex(node);
    }

    public void addEdgeToView(final Edge edge) {
        if (!parent.underlyingGraph().containsEdge(edge)) {
            throw new IllegalArgumentException("addEdgeToView: edge is not a member of the parent graph");
        }
        if (edge.getKind() != EdgeKind.REALISED) {
            throw new IllegalArgumentException("addEdgeToView: edge must be REALISED");
        }
        if (!view.containsVertex(edge.getFrom()) || !view.containsVertex(edge.getTo())) {
            throw new IllegalArgumentException("addEdgeToView: both endpoints must already be in the view");
        }
        view.addEdge(edge.getFrom(), edge.getTo(), edge);
    }

    /** Registers a synthesized conversion input node as an expandable (non-AND-required) frontier (design E2). */
    public void addConversionFrontier(final Node node) {
        conversionFrontiers.add(node);
    }

    /** The conversion frontiers expanded by the group expander; never AND-required for group SAT. */
    public Set<Node> getConversionFrontiers() {
        return Collections.unmodifiableSet(conversionFrontiers);
    }
}
