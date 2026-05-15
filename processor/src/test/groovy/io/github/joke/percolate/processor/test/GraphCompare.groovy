package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.MapperGraph

import java.util.stream.Collectors

final class GraphCompare {

    private GraphCompare() {}

    static Set<String> nodeIds(final MapperGraph graph) {
        graph.nodes().map { it.id() }.collect(Collectors.toUnmodifiableSet())
    }

    static Set<String> edgeTuples(final MapperGraph graph) {
        graph.edges().map { edgeKey(it) }.collect(Collectors.toUnmodifiableSet())
    }

    static MapperGraph union(final MapperGraph a, final MapperGraph b) {
        final result = new MapperGraph()
        a.nodes().forEach(result.&addNode)
        a.edges().forEach(result.&addEdge)
        b.nodes().forEach(result.&addNode)
        b.edges().forEach(result.&addEdge)
        result
    }

    private static String edgeKey(final Edge edge) {
        "${edge.from.id()} -> ${edge.to.id()} :: ${edge.kind}"
    }
}
