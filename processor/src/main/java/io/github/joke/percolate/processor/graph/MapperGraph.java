package io.github.joke.percolate.processor.graph;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DirectedMultigraph;

public final class MapperGraph {
    private final DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<>(Edge.class);

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

    boolean isForest() {
        final var undirected = new AsUndirectedGraph<>(graph);
        return org.jgrapht.GraphTests.isForest(undirected);
    }

    Set<Edge> edgeSet() {
        return Collections.unmodifiableSet(graph.edgeSet());
    }
}
