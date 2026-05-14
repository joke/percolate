package io.github.joke.percolate.processor.graph;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskSubgraph;

@NoArgsConstructor
public final class MapperGraph implements GraphSource {
    private final DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);
    private final Map<String, GroupCodegen> groupCodegens = new ConcurrentHashMap<>();

    public void addNode(final Node node) {
        graph.addVertex(node);
    }

    public boolean addEdge(final Edge edge) {
        graph.addVertex(edge.getFrom());
        graph.addVertex(edge.getTo());
        if (graph.edgeSet().stream().anyMatch(existing -> existing.equals(edge))) {
            return false;
        }
        graph.addEdge(edge.getFrom(), edge.getTo(), edge);
        return true;
    }

    public void apply(final GraphDelta delta) {
        delta.getNodeList().stream().forEach(this::addNode);
        delta.getEdgeList().stream().forEach(this::addEdge);
        delta.getGroupRegistrations().stream()
                .filter(r -> !groupCodegens.containsKey(r.groupId))
                .forEach(r -> groupCodegens.put(r.groupId, r.codegen));
    }

    @Override
    public Stream<Node> nodes() {
        return graph.vertexSet().stream().sorted(Comparator.comparing(Node::id));
    }

    @Override
    public Stream<Edge> edges() {
        return graph.edgeSet().stream().sorted(Comparator.naturalOrder());
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

    public ExpandedGraphView expandedView() {
        return new ExpandedGraphView(this);
    }

    public void addGroupCodegen(final String groupId, final GroupCodegen codegen) {
        if (groupCodegens.containsKey(groupId)) {
            throw new IllegalStateException("Duplicate group codegen for: " + groupId);
        }
        groupCodegens.put(groupId, codegen);
    }

    public Optional<GroupCodegen> groupCodegen(final String groupId) {
        return Optional.ofNullable(groupCodegens.get(groupId));
    }

    public boolean isAcyclic() {
        // The seed graph is a DAG but not necessarily a forest in the undirected sense,
        // since multiple directives can share common nodes (converging paths create
        // undirected cycles while remaining acyclic in the directed sense).
        return !new CycleDetector<>(graph).detectCycles();
    }

    Set<Edge> edgeSet() {
        return Collections.unmodifiableSet(graph.edgeSet());
    }

    public boolean hasSeedSubSeedCycles() {
        final var subgraph = new MaskSubgraph<Node, Edge>(
                graph, v -> false, e -> e.getKind() != EdgeKind.SEED && e.getKind() != EdgeKind.SUB_SEED);
        return new CycleDetector<>(subgraph).detectCycles();
    }
}
