package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jspecify.annotations.Nullable;

/**
 * Renders a scope slice of the bipartite graph to DOT (Petri-style): {@link Operation}s are boxes labelled with
 * their typed production {@code label}, {@link Value}s are ellipses labelled with location plus a readable type
 * (simple names + a JSpecify {@code ?}/{@code !} nullness mark per level), and {@link Dep} edges carry their port
 * id as a label. Vertices the caller marks {@code dimmed} (unreachable, by extraction cost) are greyed and dashed
 * rather than dropped, so a pruned over-emission stays distinguishable from the surviving plan. The JGraphT
 * {@link DOTExporter} owns statement structure and quoting; this class supplies only the attribute providers.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class DotRenderer {

    private static final String LABEL = "label";
    private static final String STYLE = "style";
    private static final String FILL = "fillcolor";
    private static final String UNKNOWN_TYPE = "?";
    private static final String SOURCE_FILL = "#CFE8FF";
    private static final String TARGET_FILL = "#D7F0D0";
    private static final String ELEMENT_FILL = "#FFE0B3";
    private static final String FALLBACK_FILL = "white";
    private static final String DIM_FILL = "#DDDDDD";

    /** Renders {@code scopeGraph} captioned with {@code caption}; {@code dimmed} vertices render greyed and dashed. */
    public String render(
            final Graph<GraphVertex, Dep> scopeGraph, final String caption, final Predicate<GraphVertex> dimmed) {
        final var exporter = new DOTExporter<GraphVertex, Dep>(vertex -> quote(vertex.id()));
        exporter.setGraphAttributeProvider(() -> Map.of(LABEL, attr(quote(caption))));
        exporter.setVertexAttributeProvider(vertex -> vertexAttributes(vertex, dimmed.test(vertex)));
        exporter.setEdgeAttributeProvider(DotRenderer::edgeAttributes);
        final var writer = new StringWriter();
        exporter.exportGraph(scopeGraph, writer);
        return writer.toString();
    }

    @VisibleForTesting
    static Map<String, Attribute> vertexAttributes(final GraphVertex vertex, final boolean dimmed) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        if (vertex instanceof Operation) {
            final var operation = (Operation) vertex;
            attrs.put(LABEL, attr(operation.getLabel() + " (" + operation.getWeight() + ")"));
            attrs.put("shape", attr("box"));
            attrs.put(STYLE, attr("filled"));
            attrs.put(FILL, attr("#EEEEEE"));
        } else {
            final var value = (Value) vertex;
            attrs.put(LABEL, attr(valueLabel(value)));
            attrs.put("shape", attr("ellipse"));
            attrs.put(STYLE, attr("filled"));
            attrs.put(FILL, attr(fillColor(value)));
        }
        if (dimmed) {
            attrs.put(STYLE, attr("filled,dashed"));
            attrs.put(FILL, attr(DIM_FILL));
        }
        return attrs;
    }

    private static Map<String, Attribute> edgeAttributes(final Dep dep) {
        final var attrs = new LinkedHashMap<String, Attribute>();
        dep.getPortId().ifPresent(portId -> attrs.put(LABEL, attr(portId)));
        return attrs;
    }

    @VisibleForTesting
    static String valueLabel(final Value value) {
        final var typeSegment = value.getType()
                .map(type -> formatType(type, value.getNullness().orElse(null)))
                .orElse(UNKNOWN_TYPE);
        return value.getLoc().segment() + "\\n" + typeSegment;
    }

    /**
     * The type rendered with simple names and JSpecify nullness marks: the top (reference) level carries the
     * Value's own {@code ?}/{@code !} (authoritative); each nested type argument carries {@code ?} only when its
     * own annotation declares it nullable (non-null is the unmarked JSpecify default). Non-declared kinds
     * (primitives, arrays, wildcards) fall back to their text form, unmarked.
     */
    @VisibleForTesting
    static String formatType(final TypeMirror type, final @Nullable Nullability topNullness) {
        if (type.getKind() != TypeKind.DECLARED) {
            return body(type);
        }
        return body(type) + topMark(topNullness);
    }

    @VisibleForTesting
    static String body(final TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return type.toString();
        }
        final var declared = (DeclaredType) type;
        final var name = declared.asElement().getSimpleName().toString();
        final var args = declared.getTypeArguments();
        if (args.isEmpty()) {
            return name;
        }
        final var inner = args.stream().map(DotRenderer::nested).collect(Collectors.joining(", "));
        return name + '<' + inner + '>';
    }

    private static String nested(final TypeMirror type) {
        return body(type) + (nullnessOf(type) == Nullability.NULLABLE ? "?" : "");
    }

    @VisibleForTesting
    static String topMark(final @Nullable Nullability nullness) {
        if (nullness == Nullability.NULLABLE) {
            return "?";
        }
        return nullness == Nullability.NON_NULL ? "!" : "";
    }

    private static Nullability nullnessOf(final TypeMirror type) {
        final var nullable = type.getAnnotationMirrors().stream().anyMatch(annotation -> annotation
                .getAnnotationType()
                .asElement()
                .getSimpleName()
                .contentEquals("Nullable"));
        return nullable ? Nullability.NULLABLE : Nullability.NON_NULL;
    }

    @VisibleForTesting
    static String fillColor(final Value value) {
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

    private static Attribute attr(final String value) {
        return DefaultAttribute.createAttribute(value);
    }

    @VisibleForTesting
    static String quote(final String value) {
        final var escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
