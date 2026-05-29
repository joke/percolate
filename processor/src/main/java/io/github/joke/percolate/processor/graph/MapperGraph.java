package io.github.joke.percolate.processor.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
    private final Set<Edge> edgeIndex = new HashSet<>();
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

    public boolean addEdge(final Edge edge) {
        addNode(edge.getFrom());
        addNode(edge.getTo());
        if (!edgeIndex.add(edge)) {
            return false;
        }
        graph.addEdge(edge.getFrom(), edge.getTo(), edge);
        sortedEdgesDirty = true;
        return true;
    }

    public boolean addEdgeIfAcyclic(final Edge edge) {
        if (!addEdge(edge)) {
            return false;
        }
        final var mask = new MaskSubgraph<>(graph, v -> false, e -> e.getKind() != EdgeKind.REALISED);
        if (new CycleDetector<>(mask).detectCycles()) {
            edgeIndex.remove(edge);
            graph.removeEdge(edge);
            sortedEdgesDirty = true;
            return false;
        }
        return true;
    }

    public void addGroup(final ExpansionGroup group) {
        if (!graph.containsVertex(group.getRoot())) {
            throw new IllegalArgumentException("ExpansionGroup root is not a vertex of this graph");
        }
        for (final var slot : group.getSlots()) {
            if (!graph.containsVertex(slot)) {
                throw new IllegalArgumentException("ExpansionGroup slot is not a vertex of this graph");
            }
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
        delta.getEdgeList().forEach(this::addEdge);
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
            sortedEdges = graph.edgeSet().stream().sorted().collect(Collectors.toUnmodifiableList());
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
