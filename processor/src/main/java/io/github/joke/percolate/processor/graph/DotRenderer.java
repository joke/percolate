package io.github.joke.percolate.processor.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

public final class DotRenderer {

    private static final String SOURCE_SHAPE = "box";
    private static final String TARGET_SHAPE = "oval";

    public String render(final MapperGraph graph, final TypeElement mapperType) {
        final var sb = new StringBuilder();
        final var digraphName = escapeDot(mapperType.getQualifiedName().toString());
        sb.append("digraph \"").append(digraphName).append("\" {\n");

        // Collect sorted nodes
        final var sortedNodes = graph.nodes().collect(Collectors.toList());
        final var sortedEdges = graph.edges().collect(Collectors.toList());

        // Group nodes by scope
        final var nodesByScope = new LinkedHashMap<Scope, List<Node>>();
        for (final var node : sortedNodes) {
            nodesByScope
                    .computeIfAbsent(node.getScope(), k -> new ArrayList<>())
                    .add(node);
        }

        // Render clusters sorted by scope encoding
        final var sortedEntries = nodesByScope.entrySet().stream()
                .sorted((a, b) -> a.getKey().encode().compareTo(b.getKey().encode()))
                .collect(Collectors.toList());

        for (final var entry : sortedEntries) {
            final var scope = entry.getKey();
            final var scopeNodes = entry.getValue();
            final var scopeLabel = escapeDot(scope.encode());
            sb.append("  subgraph \"cluster_").append(escapeDot(scope.encode())).append("\" {\n");
            sb.append("    label=\"").append(scopeLabel).append("\";\n");

            for (final var node : scopeNodes) {
                renderNode(sb, node);
            }
            sb.append("  }\n");
        }

        // Render edges in ascending natural order
        for (final var edge : sortedEdges) {
            renderEdge(sb, edge);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void renderNode(final StringBuilder sb, final Node node) {
        final var nodeId = escapeDot(node.id());
        final var attrs = new TreeMap<String, String>();
        attrs.put("label", escapeDot(nodeLabel(node)));

        if (node.getLoc() instanceof SourceLocation) {
            attrs.put("shape", SOURCE_SHAPE);
        } else if (node.getLoc() instanceof TargetLocation) {
            attrs.put("shape", TARGET_SHAPE);
        }

        sb.append("    \"").append(nodeId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private void renderEdge(final StringBuilder sb, final Edge edge) {
        final var fromId = escapeDot(edge.getFrom().id());
        final var toId = escapeDot(edge.getTo().id());
        final var attrs = new TreeMap<String, String>();
        attrs.put("label", String.valueOf(edge.getWeight()));

        if (edge.getDirective().isPresent()) {
            attrs.put("style", "bold");
        }

        sb.append("    \"").append(fromId).append("\" -> \"").append(toId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private void appendAttributes(final StringBuilder sb, final Map<String, String> attrs) {
        var first = true;
        for (final var entry : attrs.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            first = false;
        }
    }

    private String nodeLabel(final Node node) {
        final var loc = node.getLoc();
        if (loc == null) {
            return "?";
        }
        if (loc instanceof SourceLocation) {
            return "src[" + ((SourceLocation) loc).getPath() + "]";
        }
        if (loc instanceof TargetLocation) {
            return "tgt[" + ((TargetLocation) loc).getPath() + "]";
        }
        return "?";
    }

    static String escapeDot(final String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}
