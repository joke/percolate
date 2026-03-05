package io.github.joke.percolate.graph;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.Property;
import java.util.Set;
import org.jgrapht.Graph;

public final class GraphRenderer {

    private GraphRenderer() {}

    public static String renderConstructorNode(
            final Graph<GraphNode, GraphEdge> graph,
            final ConstructorNode constructorNode,
            final Set<String> missingParams) {

        final var parameters = constructorNode.getDescriptor().getParameters();

        final var mappedParams = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .map(ConstructorParamEdge::getParameterName)
                .collect(toSet());

        final var maxNameLen =
                parameters.stream().mapToInt(param -> param.getName().length()).max().orElse(0);

        final var paramLines = parameters.stream()
                .map(param -> formatParam(graph, constructorNode, mappedParams, maxNameLen, param))
                .collect(joining());

        final var sb = new StringBuilder();
        sb.append("\n  ConstructorNode(")
                .append(constructorNode.getTargetType().getSimpleName())
                .append("):\n")
                .append(paramLines);

        if (!missingParams.isEmpty()) {
            sb.append("\n  Suggestion: Add a matching source property or converter for: ")
                    .append(String.join(", ", missingParams));
        }

        return sb.toString();
    }

    private static String formatParam(
            final Graph<GraphNode, GraphEdge> graph,
            final ConstructorNode constructorNode,
            final Set<String> mappedParams,
            final int maxNameLen,
            final Property param) {
        final var name = param.getName();
        final var padded = String.format("%-" + maxNameLen + "s", name);
        if (mappedParams.contains(name)) {
            final var sourceDesc = findSourceDescription(graph, constructorNode, name);
            return "    " + padded + " <- " + sourceDesc + " \u2713\n";
        }
        return "    " + padded + " <- ???  \u2717  (no source mapping)\n";
    }

    private static String findSourceDescription(
            final Graph<GraphNode, GraphEdge> graph,
            final ConstructorNode constructorNode,
            final String paramName) {
        return graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .filter(paramEdge -> paramEdge.getParameterName().equals(paramName))
                .findFirst()
                .map(paramEdge -> {
                    final var source = graph.getEdgeSource(paramEdge);
                    if (source instanceof PropertyNode) {
                        return ((PropertyNode) source).name() + " ("
                                + ((PropertyNode) source).getProperty().getType() + ")";
                    }
                    if (source instanceof TypeNode) {
                        return ((TypeNode) source).getLabel() + " (" + ((TypeNode) source).getType() + ")";
                    }
                    return source.toString();
                })
                .orElse("(mapped)");
    }
}
