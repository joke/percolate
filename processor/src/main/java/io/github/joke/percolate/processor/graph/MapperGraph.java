package io.github.joke.percolate.processor.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskSubgraph;

@NoArgsConstructor
public final class MapperGraph implements GraphSource {
    private final DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);
    private final List<ExpansionGroup> expansionGroups = new ArrayList<>();
    private final List<GroupOutcome> outcomes = new ArrayList<>();

    private List<Node> sortedNodes = List.of();
    private List<Edge> sortedEdges = List.of();
    private boolean sortedNodesDirty = true;
    private boolean sortedEdgesDirty = true;

    public void addNode(final Node node) {
        if (graph.addVertex(node)) {
            sortedNodesDirty = true;
        }
    }

    /**
     * A thin append: adds both endpoints then the JGraphT edge, returning JGraphT's "was added" boolean. It holds
     * no percolate-level structural-equality dedup index — preventing duplicate parallel edges is owned by the
     * mutation callers ({@code SeedStage}'s create-gate, the expansion {@code Applier}; design D5).
     */
    public boolean addEdge(final Node from, final Node to, final Edge edge) {
        addNode(from);
        addNode(to);
        final var added = graph.addEdge(from, to, edge);
        if (added) {
            sortedEdgesDirty = true;
        }
        return added;
    }

    @Override
    public Node getEdgeSource(final Edge edge) {
        return graph.getEdgeSource(edge);
    }

    @Override
    public Node getEdgeTarget(final Edge edge) {
        return graph.getEdgeTarget(edge);
    }

    /**
     * The parallel edges between {@code from} and {@code to}, or an empty set when either endpoint is not yet a
     * vertex. Used by the expansion {@code Applier} to own edge non-duplication at the mutation site (design D5)
     * without the graph holding a standing dedup index.
     */
    public Set<Edge> getAllEdges(final Node from, final Node to) {
        if (!graph.containsVertex(from) || !graph.containsVertex(to)) {
            return Set.of();
        }
        return graph.getAllEdges(from, to);
    }

    public void addGroup(final ExpansionGroup group) {
        if (!graph.containsVertex(group.getRoot())) {
            throw new IllegalArgumentException("ExpansionGroup root is not a vertex of this graph");
        }
        expansionGroups.add(group);
    }

    public Stream<ExpansionGroup> groups() {
        return expansionGroups.stream();
    }

    public void recordGroupOutcome(final GroupOutcome outcome) {
        outcomes.add(outcome);
    }

    public Stream<GroupOutcome> groupOutcomes() {
        return outcomes.stream();
    }

    public void apply(final GraphDelta delta) {
        delta.getNodeList().forEach(this::addNode);
        delta.getEdgeList().forEach(entry -> addEdge(entry.getFrom(), entry.getTo(), entry.getEdge()));
        delta.getGroups().forEach(this::addGroup);
    }

    @Override
    public Stream<Node> nodes() {
        if (sortedNodesDirty) {
            sortedNodes = graph.vertexSet().stream()
                    .sorted(Comparator.comparing(Node::id))
                    .collect(Collectors.toUnmodifiableList());
            sortedNodesDirty = false;
        }
        return sortedNodes.stream();
    }

    @Override
    public Stream<Edge> edges() {
        if (sortedEdgesDirty) {
            sortedEdges = graph.edgeSet().stream().sorted(EdgeOrder.by(graph)).collect(Collectors.toUnmodifiableList());
            sortedEdgesDirty = false;
        }
        return sortedEdges.stream();
    }

    public Stream<Node> nodesByScope(final Scope scope) {
        return nodes().filter(n -> n.getScope().equals(scope));
    }

    public int nodeCount() {
        return graph.vertexSet().size();
    }

    public int edgeCount() {
        return graph.edgeSet().size();
    }

    public RealisedSubgraph realisedSubgraph() {
        final var mask = new MaskSubgraph<>(graph, v -> false, e -> e.getKind() != EdgeKind.REALISED);
        return new RealisedSubgraph(mask, this);
    }

    public TransformsView transformsView() {
        final var mask = new MaskSubgraph<>(graph, v -> false, e -> e.getKind() != EdgeKind.REALISED);
        return new TransformsView(mask, this);
    }

    public PlanView planView() {
        return PlanView.of(this);
    }

    public boolean isAcyclic() {
        return !new CycleDetector<>(graph).detectCycles();
    }

    Set<Edge> edgeSet() {
        return Collections.unmodifiableSet(graph.edgeSet());
    }

    public DirectedMultigraph<Node, Edge> underlyingGraph() {
        return graph;
    }
}
