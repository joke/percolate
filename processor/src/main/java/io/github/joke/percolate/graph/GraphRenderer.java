package io.github.joke.percolate.graph;

import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.model.Property;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.graph.DirectedWeightedMultigraph;

/** Produces an ASCII representation of constructor node state for error diagnostics. */
public final class GraphRenderer {

    private GraphRenderer() {}

    public static String renderConstructorNode(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph,
            ConstructorNode constructorNode,
            Set<String> missingParams) {

        List<Property> parameters = constructorNode.getDescriptor().getParameters();

        Set<String> mappedParams = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .map(ConstructorParamEdge::getParameterName)
                .collect(Collectors.toSet());

        int maxNameLen =
                parameters.stream().mapToInt(p -> p.getName().length()).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("ConstructorNode(")
                .append(constructorNode.getTargetType().getSimpleName())
                .append("):\n");

        List<String> lines = parameters.stream()
                .map(param -> {
                    String name = param.getName();
                    String padded = String.format("%-" + maxNameLen + "s", name);
                    if (mappedParams.contains(name)) {
                        return "  " + padded + " <- (mapped) \u2713";
                    } else {
                        return "  " + padded + " <- ???       \u2717  (no source mapping)";
                    }
                })
                .collect(toList());

        sb.append(String.join("\n", lines));
        sb.append("\n\nSuggestion: Add a matching source property for: ").append(String.join(", ", missingParams));

        return sb.toString();
    }
}
