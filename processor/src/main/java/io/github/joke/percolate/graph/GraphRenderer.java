package io.github.joke.percolate.graph;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.Property;
import java.util.List;
import java.util.Set;
import org.jgrapht.Graph;

public final class GraphRenderer {

    private GraphRenderer() {}

    public static String renderConstructorNode(
            Graph<GraphNode, GraphEdge> graph,
            ConstructorNode constructorNode,
            Set<String> missingParams) {

        List<Property> parameters = constructorNode.getDescriptor().getParameters();

        Set<String> mappedParams = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .map(ConstructorParamEdge::getParameterName)
                .collect(toSet());

        int maxNameLen = parameters.stream().mapToInt(p -> p.getName().length()).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("\n  ConstructorNode(")
                .append(constructorNode.getTargetType().getSimpleName())
                .append("):\n");

        for (Property param : parameters) {
            String name = param.getName();
            String padded = String.format("%-" + maxNameLen + "s", name);
            if (mappedParams.contains(name)) {
                String sourceDesc = findSourceDescription(graph, constructorNode, name);
                sb.append("    ").append(padded).append(" <- ").append(sourceDesc).append(" \u2713\n");
            } else {
                sb.append("    ").append(padded).append(" <- ???  \u2717  (no source mapping)\n");
            }
        }

        if (!missingParams.isEmpty()) {
            sb.append("\n  Suggestion: Add a matching source property or converter for: ")
                    .append(String.join(", ", missingParams));
        }

        return sb.toString();
    }

    private static String findSourceDescription(
            Graph<GraphNode, GraphEdge> graph, ConstructorNode constructorNode, String paramName) {
        return graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .filter(e -> e.getParameterName().equals(paramName))
                .findFirst()
                .map(e -> {
                    GraphNode source = graph.getEdgeSource(e);
                    if (source instanceof PropertyNode) {
                        return ((PropertyNode) source).name() + " (" + ((PropertyNode) source).getProperty().getType() + ")";
                    }
                    if (source instanceof TypeNode) {
                        return ((TypeNode) source).getLabel() + " (" + ((TypeNode) source).getType() + ")";
                    }
                    return source.toString();
                })
                .orElse("(mapped)");
    }
}
