package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import java.util.Set;
import java.util.stream.Collectors;

public final class GraphCompare {

    private GraphCompare() {}

    public static Set<String> nodeIds(final MapperGraph graph) {
        return graph.nodes().map(Node::id).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> edgeTuples(final MapperGraph graph) {
        return graph.edges().map(GraphCompare::edgeKey).collect(Collectors.toUnmodifiableSet());
    }

    public static MapperGraph union(final MapperGraph a, final MapperGraph b) {
        final var result = new MapperGraph();
        a.nodes().forEach(result::addNode);
        a.edges().forEach(result::addEdge);
        b.nodes().forEach(result::addNode);
        b.edges().forEach(result::addEdge);
        return result;
    }

    private static String edgeKey(final Edge edge) {
        return edge.getFrom().id() + " -> " + edge.getTo().id() + " :: " + edge.getKind();
    }
}
