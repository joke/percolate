package io.github.joke.percolate.processor.graph;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class DotRenderer {

    private static final String SOURCE_SHAPE = "box";
    private static final String TARGET_SHAPE = "oval";
    private static final String PHANTOM_SHAPE = "diamond";
    private static final String SENTINEL_LABEL = "∞";

    private static final Map<EdgeKind, String> KIND_STYLE = new ConcurrentHashMap<>();

    static {
        KIND_STYLE.put(EdgeKind.SEED, "solid");
        KIND_STYLE.put(EdgeKind.REALISED, "dashed");
        KIND_STYLE.put(EdgeKind.MARKER, "dotted");
        KIND_STYLE.put(EdgeKind.SUB_SEED, "bold");
    }

    public String render(final MapperGraph graph, final TypeElement mapperType) {
        final var sb = new StringBuilder(128);
        sb.append("digraph \"")
                .append(escapeDot(mapperType.getQualifiedName().toString()))
                .append("\" {\n");

        final var sortedNodes = graph.nodes().collect(toList());
        final var nodesByScope = buildNodesByScope(sortedNodes);
        final var phantomNodesByParentScope = buildPhantomNodesByParentScope(sortedNodes);

        final var sortedScopes = nodesByScope.entrySet().stream()
                .sorted((a, b) -> a.getKey().encode().compareTo(b.getKey().encode()))
                .collect(toList());
        for (final var entry : sortedScopes) {
            renderCluster(sb, entry.getKey(), entry.getValue(), phantomNodesByParentScope);
        }

        for (final var edge : graph.edges().collect(toList())) {
            renderEdge(sb, edge);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private Map<Scope, List<Node>> buildNodesByScope(final List<Node> nodes) {
        return nodes.stream().collect(groupingBy(Node::getScope, LinkedHashMap::new, toList()));
    }

    private Map<Scope, List<Node>> buildPhantomNodesByParentScope(final List<Node> nodes) {
        return nodes.stream()
                .filter(n -> n.getLoc() instanceof ElementLocation)
                .collect(groupingBy(n -> {
                    final var parent = n.getParent();
                    if (parent.isEmpty()) {
                        throw new IllegalStateException("Phantom node without parent: " + n.id());
                    }
                    return parent.get().getScope();
                }, LinkedHashMap::new, toList()));
    }

    private void renderCluster(
            final StringBuilder sb,
            final Scope scope,
            final List<Node> scopeNodes,
            final Map<Scope, List<Node>> phantomNodesByParentScope) {
        final var scopeLabel = escapeDot(scope.encode());
        sb.append("  subgraph \"cluster_")
                .append(scopeLabel)
                .append("\" {\n    label=\"")
                .append(scopeLabel)
                .append("\";\n");
        for (final var node : scopeNodes) {
            renderNode(sb, node);
        }
        if (phantomNodesByParentScope.containsKey(scope)) {
            for (final var phantom : phantomNodesByParentScope.get(scope)) {
                renderNode(sb, phantom);
            }
        }
        sb.append("  }\n");
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

        final var weightLabel =
                Weights.isSentinel(edge.getWeight()) ? SENTINEL_LABEL : String.valueOf(edge.getWeight());

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

        final var style = KIND_STYLE.getOrDefault(edge.getKind(), "solid");
        attrs.put("style", style);

        sb.append("    \"").append(fromId).append("\" -> \"").append(toId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private void appendAttributes(final StringBuilder sb, final Map<String, String> attrs) {
        sb.append(attrs.entrySet().stream()
                .map(e -> e.getKey() + "=\"" + e.getValue() + '"')
                .collect(joining(", ")));
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
            return loc.segment();
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
