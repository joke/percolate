package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.GraphRenderer;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.model.Property;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.tools.Diagnostic;
import org.jgrapht.graph.DirectedWeightedMultigraph;

@RoundScoped
public class ValidateStage {

    private final Messager messager;

    @Inject
    ValidateStage(Messager messager) {
        this.messager = messager;
    }

    public ValidationResult execute(GraphResult graphResult) {
        DirectedWeightedMultigraph<GraphNode, GraphEdge> graph = graphResult.getGraph();
        boolean hasFatalErrors = false;

        Set<ConstructorNode> constructorNodes = graph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .collect(toSet());

        for (ConstructorNode constructorNode : constructorNodes) {
            Set<String> mappedParams = graph.incomingEdgesOf(constructorNode).stream()
                    .filter(ConstructorParamEdge.class::isInstance)
                    .map(ConstructorParamEdge.class::cast)
                    .map(ConstructorParamEdge::getParameterName)
                    .collect(toSet());

            List<Property> parameters = constructorNode.getDescriptor().getParameters();
            Set<String> missingParams = new LinkedHashSet<>();
            for (Property param : parameters) {
                if (!mappedParams.contains(param.getName())) {
                    missingParams.add(param.getName());
                }
            }

            if (!missingParams.isEmpty()) {
                hasFatalErrors = true;
                String rendered = GraphRenderer.renderConstructorNode(graph, constructorNode, missingParams);
                String message = "Unmapped target properties in "
                        + constructorNode.getTargetType().getQualifiedName() + ": "
                        + String.join(", ", missingParams) + "\n" + rendered;
                messager.printMessage(Diagnostic.Kind.ERROR, message);
            }
        }

        return new ValidationResult(graphResult, hasFatalErrors);
    }
}
