package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.GroupCodegen;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.AnnotatedConstruct;
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
    private final Map<Node, AnnotatedConstruct> slotConsumerContracts;

    private ExpansionGroup(
            final Node root,
            final List<Node> slots,
            final GroupCodegen codegen,
            final String strategyClassFqn,
            final AsSubgraph<Node, Edge> view,
            final MapperGraph parent,
            final Map<Node, AnnotatedConstruct> slotConsumerContracts) {
        this.root = root;
        this.slots = slots;
        this.codegen = codegen;
        this.strategyClassFqn = strategyClassFqn;
        this.view = view;
        this.parent = parent;
        this.slotConsumerContracts = slotConsumerContracts;
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
            final Map<Node, AnnotatedConstruct> slotConsumerContracts) {
        final var underlying = parent.underlyingGraph();
        validateMembership(underlying, root, slots, initialEdges);
        final var vertices = new HashSet<Node>(slots.size() + 1);
        vertices.add(root);
        vertices.addAll(slots);
        final var view = new AsSubgraph<>(underlying, vertices, new HashSet<>(initialEdges));
        final var contractsCopy = new IdentityHashMap<Node, AnnotatedConstruct>(slotConsumerContracts.size());
        for (final var entry : slotConsumerContracts.entrySet()) {
            contractsCopy.put(entry.getKey(), entry.getValue());
        }
        return new ExpansionGroup(root, List.copyOf(slots), codegen, strategyClassFqn, view, parent, contractsCopy);
    }

    public @Nullable AnnotatedConstruct consumerContractFor(final Node slot) {
        return slotConsumerContracts.get(slot);
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
}
