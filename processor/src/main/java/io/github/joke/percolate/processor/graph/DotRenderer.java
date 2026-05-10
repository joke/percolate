package io.github.joke.percolate.processor.graph;

import lombok.NoArgsConstructor;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

@NoArgsConstructor
public final class DotRenderer {

    private static final String SOURCE_SHAPE = "box";
    private static final String TARGET_SHAPE = "oval";
    private static final String PHANTOM_SHAPE = "diamond";
    private static final String SENTINEL_LABEL = "\u221E";

    private static final Map<EdgeKind, String> KIND_STYLE = new ConcurrentHashMap<>();

    static {
        KIND_STYLE.put(EdgeKind.SEED, "solid");
        KIND_STYLE.put(EdgeKind.REALISED, "dashed");
        KIND_STYLE.put(EdgeKind.MARKER, "dotted");
        KIND_STYLE.put(EdgeKind.SUB_SEED, "bold");
    }

    public String render(final MapperGraph graph, final TypeElement mapperType) {
        final var sb = new StringBuilder(128);
        final var digraphName = escapeDot(mapperType.getQualifiedName().toString());
        sb.append("digraph \"").append(digraphName).append("\" {\n");

        final var sortedNodes = graph.nodes().collect(toList());
        final var sortedEdges = graph.edges().collect(toList());

        // Group nodes by scope for cluster placement
        final var nodesByScope = new LinkedHashMap<Scope, List<Node>>();
        for (final var node : sortedNodes) {
            nodesByScope
                    .computeIfAbsent(node.getScope(), k -> new ArrayList<>())
                    .add(node);
        }

        // Separate phantom nodes and group them by parent scope
        final var phantomNodesByParentScope = new LinkedHashMap<Scope, List<Node>>();
        for (final var node : sortedNodes) {
            if (node.getLoc() instanceof ElementLocation) {
                final var parent = node.getParent();
                if (parent.isEmpty()) {
                    throw new IllegalStateException("Phantom node without parent: " + node.id());
                }
                final var parentScope = parent.get().getScope();
                phantomNodesByParentScope
                        .computeIfAbsent(parentScope, k -> new ArrayList<>())
                        .add(node);
            }
        }

        // Render clusters sorted by scope encoding
        final var sortedEntries = nodesByScope.entrySet().stream()
                .sorted((a, b) -> a.getKey().encode().compareTo(b.getKey().encode()))
                .collect(toList());

        for (final var entry : sortedEntries) {
            final var scope = entry.getKey();
            final var scopeNodes = entry.getValue();
            final var scopeLabel = escapeDot(scope.encode());
            sb.append("  subgraph \"cluster_")
                    .append(scopeLabel)
                    .append("\" {\n    label=\"")
                    .append(scopeLabel)
                    .append("\";\n");

            for (final var node : scopeNodes) {
                renderNode(sb, node);
            }

            // Render phantom nodes belonging to this scope's cluster
            if (phantomNodesByParentScope.containsKey(scope)) {
                for (final var phantom : phantomNodesByParentScope.get(scope)) {
                    renderNode(sb, phantom);
                }
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
        } else if (node.getLoc() instanceof ElementLocation) {
            attrs.put("shape", PHANTOM_SHAPE);
        }

        sb.append("    \"").append(nodeId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private void renderEdge(final StringBuilder sb, final Edge edge) {
        final var fromId = escapeDot(edge.getFrom().id());
        final var toId = escapeDot(edge.getTo().id());
        final var attrs = new TreeMap<String, String>();

        // Weight label: ∞ for sentinel, numeric value otherwise
        final var weightLabel =
                edge.getWeight() == Weights.SENTINEL_UNREALISED ? SENTINEL_LABEL : String.valueOf(edge.getWeight());

        // Build label with kind marker, weight, and optional strategy FQN
        final var labelParts = new ArrayList<String>();
        labelParts.add(edge.getKind().name());
        labelParts.add(weightLabel);
        if (edge.getDirective().isPresent()) {
            labelParts.add("directive");
        }
        if (edge.getStrategyClassFqn().isPresent()) {
            labelParts.add(edge.getStrategyClassFqn().get());
        }
        attrs.put("label", escapeDot(String.join(" | ", labelParts)));

        if (edge.getGroupId().isPresent()) {
            attrs.put("group", escapeDot(edge.getGroupId().get()));
        }

        // Style keyed off EdgeKind
        final var style = KIND_STYLE.getOrDefault(edge.getKind(), "solid");
        attrs.put("style", style);

        sb.append("    \"").append(fromId).append("\" -> \"").append(toId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private void appendAttributes(final StringBuilder sb, final Map<String, String> attrs) {
        final var first = new boolean[] {true};
        for (final var entry : attrs.entrySet()) {
            if (!first[0]) {
                sb.append(", ");
            }
            final var attrLine = entry.getKey() + "=\"" + entry.getValue() + '"';
            sb.append(attrLine);
            first[0] = false;
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
        if (loc instanceof ElementLocation) {
            return "elem";
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
