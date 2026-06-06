package io.github.joke.percolate.processor.graph;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

/**
 * Renders a single scope's slice of a {@link GraphSource} to DOT by delegating to JGraphT's {@link DOTExporter}.
 * The exporter owns statement structure, identifier quoting, and double-quote escaping; this class only supplies
 * the attribute providers. Node roles are distinguished by {@code fillcolor} on a uniform {@code box} shape;
 * {@code SEED} edges recede to grey while {@code REALISED} edges keep the heaviest stroke.
 */
@NoArgsConstructor
public final class DotRenderer {

    private static final String UNKNOWN_TYPE = "?";
    private static final String SENTINEL_LABEL = "∞";

    private static final String SOURCE_FILL = "#CFE8FF";
    private static final String TARGET_FILL = "#D7F0D0";
    private static final String ELEMENT_FILL = "#FFE0B3";
    private static final String FALLBACK_FILL = "white";

    private static final String SEED_GREY = "grey60";
    private static final String REALISED_COLOR = "black";
    private static final String REALISED_PENWIDTH = "2.0";
    private static final String DEFAULT_PENWIDTH = "1.0";

    /**
     * Renders {@code scopeGraph} to a DOT string captioned with {@code caption} (the human-readable scope
     * description). The caption is emitted as a graph-level {@code label} attribute.
     */
    public String render(final Graph<Node, Edge> scopeGraph, final String caption) {
        final var exporter = new DOTExporter<Node, Edge>(DotRenderer::dotId);
        exporter.setGraphAttributeProvider(() -> Map.of("label", attr(quote(caption))));
        exporter.setVertexAttributeProvider(this::vertexAttributes);
        exporter.setEdgeAttributeProvider(this::edgeAttributes);
        final var writer = new StringWriter();
        exporter.exportGraph(scopeGraph, writer);
        return writer.toString();
    }

    private Map<String, Attribute> vertexAttributes(final Node node) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        attrs.put("label", attr(nodeLabel(node)));
        attrs.put("shape", attr("box"));
        attrs.put("style", attr("filled"));
        attrs.put("fillcolor", attr(fillColor(node)));
        return attrs;
    }

    private static String fillColor(final Node node) {
        final var loc = node.getLoc();
        if (loc instanceof SourceLocation) {
            return SOURCE_FILL;
        }
        if (loc instanceof TargetLocation) {
            return TARGET_FILL;
        }
        if (loc instanceof ElementLocation) {
            return ELEMENT_FILL;
        }
        return FALLBACK_FILL;
    }

    private Map<String, Attribute> edgeAttributes(final Edge edge) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        attrs.put("label", attr(buildEdgeLabel(edge)));
        final var kind = edge.getKind();
        if (kind == EdgeKind.REALISED) {
            attrs.put("color", attr(REALISED_COLOR));
            attrs.put("penwidth", attr(REALISED_PENWIDTH));
        } else if (kind == EdgeKind.SEED) {
            attrs.put("color", attr(SEED_GREY));
            attrs.put("fontcolor", attr(SEED_GREY));
            attrs.put("penwidth", attr(DEFAULT_PENWIDTH));
        } else {
            attrs.put("penwidth", attr(DEFAULT_PENWIDTH));
        }
        return attrs;
    }

    private String buildEdgeLabel(final Edge edge) {
        final var kind = edge.getKind();
        if (kind == EdgeKind.REALISED) {
            return buildRealisedLabel(edge);
        }
        if (kind == EdgeKind.SEED) {
            return buildSeedLabel(edge);
        }
        return "";
    }

    private String buildRealisedLabel(final Edge edge) {
        final var weightLabel = weightLabel(edge.getWeight());
        final var strategyShort =
                edge.getStrategyClassFqn().map(this::simpleClassName).orElse("");
        if (strategyShort.isEmpty()) {
            return weightLabel;
        }
        return strategyShort + " (" + weightLabel + ")";
    }

    private String buildSeedLabel(final Edge edge) {
        final var parts = new ArrayList<String>();
        parts.add("SEED");
        parts.add(weightLabel(edge.getWeight()));
        if (edge.getDirective().isPresent()) {
            parts.add("directive");
        }
        edge.getStrategyClassFqn().ifPresent(parts::add);
        return String.join(" | ", parts);
    }

    private static String weightLabel(final int weight) {
        return Weights.isSentinel(weight) ? SENTINEL_LABEL : String.valueOf(weight);
    }

    private String simpleClassName(final String fqn) {
        final var lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    private String nodeLabel(final Node node) {
        final var loc = node.getLoc();
        if (loc == null) {
            return "?\\n?";
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
                simplifyTypeName(node.getType().map(Object::toString).orElse(UNKNOWN_TYPE));
        return locationSegment + "\\n" + typeSegment;
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

    private static Attribute attr(final String value) {
        return DefaultAttribute.createAttribute(value);
    }

    /**
     * The node's fully qualified {@link Node#id()} wrapped as a quoted DOT identifier. {@link DOTExporter} accepts
     * a pre-quoted string as a valid id and writes it verbatim, so the fully qualified id is preserved in the
     * output.
     */
    private static String dotId(final Node node) {
        return quote(node.id());
    }

    /**
     * Wraps {@code value} in double quotes with internal quotes and backslashes escaped. Needed for the graph-level
     * caption: {@link DOTExporter} renders graph attributes as bare {@code key=value} without quoting, unlike vertex
     * and edge attribute values which it quotes itself.
     */
    private static String quote(final String value) {
        final var escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
