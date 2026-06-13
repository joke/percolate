package io.github.joke.percolate.processor.graph;

import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

/**
 * Renders a scope slice of the bipartite graph to DOT (Petri-style): {@link Operation}s are boxes, {@link Value}s
 * are ellipses filled by location role, and {@link Dep} edges carry their port id as a label. The JGraphT
 * {@link DOTExporter} owns statement structure and quoting; this class supplies only the attribute providers.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class DotRenderer {

    private static final String LABEL = "label";
    private static final String UNKNOWN_TYPE = "?";
    private static final String SOURCE_FILL = "#CFE8FF";
    private static final String TARGET_FILL = "#D7F0D0";
    private static final String ELEMENT_FILL = "#FFE0B3";
    private static final String FALLBACK_FILL = "white";

    /** Renders {@code scopeGraph} to a DOT string captioned with {@code caption}. */
    public String render(final Graph<GraphVertex, Dep> scopeGraph, final String caption) {
        final var exporter = new DOTExporter<GraphVertex, Dep>(vertex -> quote(vertex.id()));
        exporter.setGraphAttributeProvider(() -> Map.of(LABEL, attr(quote(caption))));
        exporter.setVertexAttributeProvider(DotRenderer::vertexAttributes);
        exporter.setEdgeAttributeProvider(DotRenderer::edgeAttributes);
        final var writer = new StringWriter();
        exporter.exportGraph(scopeGraph, writer);
        return writer.toString();
    }

    private static Map<String, Attribute> vertexAttributes(final GraphVertex vertex) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        if (vertex instanceof Operation) {
            final var operation = (Operation) vertex;
            attrs.put(LABEL, attr(operation.getLabel() + " (" + operation.getWeight() + ")"));
            attrs.put("shape", attr("box"));
            attrs.put("style", attr("filled"));
            attrs.put("fillcolor", attr("#EEEEEE"));
        } else {
            final var value = (Value) vertex;
            attrs.put(LABEL, attr(valueLabel(value)));
            attrs.put("shape", attr("ellipse"));
            attrs.put("style", attr("filled"));
            attrs.put("fillcolor", attr(fillColor(value)));
        }
        return attrs;
    }

    private static Map<String, Attribute> edgeAttributes(final Dep dep) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        dep.getPortId().ifPresent(portId -> attrs.put(LABEL, attr(portId)));
        return attrs;
    }

    private static String valueLabel(final Value value) {
        final var typeSegment =
                simplifyTypeName(value.getType().map(Object::toString).orElse(UNKNOWN_TYPE));
        return value.getLoc().segment() + "\\n" + typeSegment;
    }

    private static String fillColor(final Value value) {
        final var loc = value.getLoc();
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

    static String simplifyTypeName(final String typeName) {
        if (UNKNOWN_TYPE.equals(typeName)) {
            return UNKNOWN_TYPE;
        }
        return typeName.replaceAll("(?<![^<>])java\\.lang\\.", "");
    }

    private static Attribute attr(final String value) {
        return DefaultAttribute.createAttribute(value);
    }

    private static String quote(final String value) {
        final var escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
