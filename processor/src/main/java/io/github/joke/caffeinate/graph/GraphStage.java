package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.analysis.MappingMethod;
import io.github.joke.caffeinate.validation.ValidationResult;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import javax.inject.Inject;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class GraphStage {

    @Inject
    public GraphStage() {
    }

    public GraphResult build(ValidationResult validation) {
        DefaultDirectedGraph<TypeMirror, MethodEdge> typeGraph =
                new DefaultDirectedGraph<>(MethodEdge.class);
        DirectedAcyclicGraph<MappingMethod, DefaultEdge> methodGraph =
                new DirectedAcyclicGraph<>(DefaultEdge.class);

        for (MapperDescriptor descriptor : validation.getMappers()) {
            for (MappingMethod method : descriptor.getMethods()) {
                methodGraph.addVertex(method);

                for (VariableElement param : method.getParameters()) {
                    TypeMirror sourceType = param.asType();
                    TypeMirror targetType = method.getTargetType().asType();
                    if (!typeGraph.containsVertex(sourceType)) typeGraph.addVertex(sourceType);
                    if (!typeGraph.containsVertex(targetType)) typeGraph.addVertex(targetType);
                    typeGraph.addEdge(sourceType, targetType, new MethodEdge(method));
                }
            }
        }

        return new GraphResult(typeGraph, methodGraph);
    }
}
