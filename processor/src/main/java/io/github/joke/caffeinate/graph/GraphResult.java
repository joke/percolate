package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MappingMethod;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import javax.lang.model.type.TypeMirror;
import java.util.Optional;
import java.util.Set;

public final class GraphResult {
    private final DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph;
    private final DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph;

    public GraphResult(
            DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph,
            DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph) {
        this.typeGraph = typeGraph;
        this.methodGraph = methodGraph;
    }

    public Optional<MappingMethod> resolverFor(TypeMirror source, TypeMirror target) {
        Set<MethodEdge> edges = typeGraph.getAllEdges(source, target);
        if (edges == null) {
            return Optional.empty();
        }
        return edges.stream()
                .map(MethodEdge::getMethod)
                .findFirst();
    }

    public DefaultDirectedGraph<TypeMirror, MethodEdge> getTypeGraph() { return typeGraph; }
    public DirectedAcyclicGraph<MappingMethod, DefaultEdge> getMethodGraph() { return methodGraph; }
}
