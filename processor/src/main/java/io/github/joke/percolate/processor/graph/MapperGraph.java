package io.github.joke.percolate.processor.graph;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskSubgraph;

public final class MapperGraph {
    private final DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);
    private final Map<String, GroupCodegen> groupCodegens = new HashMap<>();

    public void addNode(final Node node) {
        graph.addVertex(node);
    }

    public void addEdge(final Edge edge) {
        graph.addVertex(edge.getFrom());
        graph.addVertex(edge.getTo());
        if (graph.containsEdge(edge)) {
            return;
        }
        graph.addEdge(edge.getFrom(), edge.getTo(), edge);
    }

    public Stream<Node> nodes() {
        return graph.vertexSet().stream().sorted(Comparator.comparing(Node::id));
    }

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
        final var mask = new MaskSubgraph<>(graph,
                v -> false,
                e -> e.getKind() != EdgeKind.REALISED);
        return new RealisedSubgraph(mask, this);
    }

    public void addGroupCodegen(final String groupId, final GroupCodegen codegen) {
        if (groupCodegens.containsKey(groupId)) {
            throw new IllegalStateException("Duplicate group codegen for: " + groupId);
        }
        groupCodegens.put(groupId, codegen);
    }

    public java.util.Optional<GroupCodegen> groupCodegen(final String groupId) {
        return java.util.Optional.ofNullable(groupCodegens.get(groupId));
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
}
