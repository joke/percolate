package io.github.joke.percolate.processor.graph;

import lombok.NoArgsConstructor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@NoArgsConstructor
public final class DotRenderer {

    private static final String SOURCE_SHAPE = "box";
    private static final String TARGET_SHAPE = "oval";
    private static final String PHANTOM_SHAPE = "diamond";
    private static final String SENTINEL_LABEL = "\u221E";
    private static final String SOLID_STYLE = "solid";
    private static final String UNKNOWN_TYPE = "?";

    private static final Map<EdgeKind, String> KIND_STYLE = new ConcurrentHashMap<>();

    static {
        KIND_STYLE.put(EdgeKind.SEED, SOLID_STYLE);
        KIND_STYLE.put(EdgeKind.REALISED, SOLID_STYLE);
        KIND_STYLE.put(EdgeKind.SUB_SEED, SOLID_STYLE);
    }

    public String render(final GraphSource source, final TypeElement mapperType) {
        final var sb = new StringBuilder(128);
        sb.append("digraph \"")
                .append(escapeDot(mapperType.getQualifiedName().toString()))
                .append("\" {\n");

        final var sortedNodes = source.nodes().collect(toList());
        final var nodesByScope = buildNodesByScope(sortedNodes);
        final var phantomNodesByParentScope = buildPhantomNodesByParentScope(sortedNodes);

        final var sortedScopes = nodesByScope.entrySet().stream()
                .sorted((a, b) -> a.getKey().encode().compareTo(b.getKey().encode()))
                .collect(toList());
        for (final var entry : sortedScopes) {
            renderCluster(sb, entry.getKey(), entry.getValue(), phantomNodesByParentScope);
        }

        for (final var edge : source.edges().collect(toList())) {
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
                .collect(groupingBy(
                        n -> {
                            final var parent = n.getParent();
                            if (parent.isEmpty()) {
                                throw new IllegalStateException("Phantom node without parent: " + n.id());
                            }
                            return parent.get().getScope();
                        },
                        LinkedHashMap::new,
                        toList()));
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

        final var label = buildEdgeLabel(edge);
        attrs.put("label", escapeDot(label));

        if (edge.getGroupId().isPresent()) {
            attrs.put("group", escapeDot(edge.getGroupId().get()));
        }

        final var style = KIND_STYLE.getOrDefault(edge.getKind(), SOLID_STYLE);
        attrs.put("style", style);

        final var penwidth = edgePenwidth(edge.getKind());
        attrs.put("penwidth", String.valueOf(penwidth));

        final var colorOpt = edgeColor(edge.getKind());
        if (colorOpt.isPresent()) {
            attrs.put("color", colorOpt.get());
        }

        sb.append("    \"").append(fromId).append("\" -> \"").append(toId).append("\" [");
        appendAttributes(sb, attrs);
        sb.append("];\n");
    }

    private String buildEdgeLabel(final Edge edge) {
        final EdgeKind kind = edge.getKind();
        if (kind == EdgeKind.REALISED) {
            return buildRealisedLabel(edge);
        }
        if (kind == EdgeKind.SUB_SEED) {
            return "SUB_SEED";
        }
        if (kind == EdgeKind.SEED) {
            return buildSeedLabel(edge);
        }
        return buildMarkerLabel(edge);
    }

    private String buildRealisedLabel(final Edge edge) {
        final var weightLabel =
                Weights.isSentinel(edge.getWeight()) ? SENTINEL_LABEL : String.valueOf(edge.getWeight());
        final var strategyShort =
                edge.getStrategyClassFqn().map(this::simpleClassName).orElse("");
        if (strategyShort.isEmpty()) {
            return weightLabel;
        }
        return strategyShort + " (" + weightLabel + ")";
    }

    private String buildSeedLabel(final Edge edge) {
        final var weightLabel =
                Weights.isSentinel(edge.getWeight()) ? SENTINEL_LABEL : String.valueOf(edge.getWeight());
        final var parts = new ArrayList<String>();
        parts.add("SEED");
        parts.add(weightLabel);
        if (edge.getDirective().isPresent()) {
            parts.add("directive");
        }
        if (edge.getStrategyClassFqn().isPresent()) {
            parts.add(edge.getStrategyClassFqn().get());
        }
        return String.join(" | ", parts);
    }

    private String buildMarkerLabel(final Edge edge) {
        final var weightLabel =
                Weights.isSentinel(edge.getWeight()) ? SENTINEL_LABEL : String.valueOf(edge.getWeight());
        return "MARKER | " + weightLabel;
    }

    private String simpleClassName(final String fqn) {
        final var lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0) {
            return fqn.substring(lastDot + 1);
        }
        return fqn;
    }

    private static String edgePenwidth(final EdgeKind kind) {
        if (kind == EdgeKind.REALISED) {
            return "2.0";
        }
        return "1.0";
    }

    private static java.util.Optional<String> edgeColor(final EdgeKind kind) {
        if (kind == EdgeKind.SUB_SEED) {
            return java.util.Optional.of("#666666");
        }
        return java.util.Optional.empty();
    }

    private void appendAttributes(final StringBuilder sb, final Map<String, String> attrs) {
        sb.append(attrs.entrySet().stream()
                .map(e -> e.getKey() + "=\"" + e.getValue() + '"')
                .collect(joining(", ")));
    }

    private String nodeLabel(final Node node) {
        final var loc = node.getLoc();
        if (loc == null) {
            return "?\n?";
        }
        final String locationSegment;
        if (loc instanceof SourceLocation) {
            locationSegment = "src[" + ((SourceLocation) loc).getPath() + "]";
        } else if (loc instanceof TargetLocation) {
            locationSegment = "tgt[" + ((TargetLocation) loc).getPath() + "]";
        } else if (loc instanceof ElementLocation) {
            locationSegment = loc.segment();
        } else {
            locationSegment = "?";
        }
        final var typeSegment =
                simplifyTypeName(node.getType().map(TypeMirror::toString).orElse("?"));
        return locationSegment + "\n" + typeSegment;
    }

    static String simplifyTypeName(final String typeName) {
        if (UNKNOWN_TYPE.equals(typeName)) {
            return UNKNOWN_TYPE;
        }
        return stripJavaLangRecursive(typeName);
    }

    private static String stripJavaLangRecursive(final String typeName) {
        return typeName.replaceAll("(?<![^<>])java\\.lang\\.", "");
    }

    static String escapeDot(final String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}
