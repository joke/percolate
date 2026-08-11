package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import org.jgrapht.Graph;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Nullability.NULLABLE;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.type.TypeKind.DECLARED;

// Renders a scope slice of the bipartite graph to DOT (Petri-style): Operations are boxes labelled with their
// typed production label, Values are ellipses labelled with location plus a readable type (simple names + a
// JSpecify ?/! nullness mark per level), and Dep edges carry their port id as a label. Vertices the caller
// marks dimmed (unreachable, by extraction cost) are greyed and dashed rather than dropped, so a pruned over-
// emission stays distinguishable from the surviving plan.
//
// When withRefusals is set, each Value's recorded refusals are drawn beside it as negative space: one note-
// shaped node per refusal, in the order they were recorded, carrying the refusal's message as opaque text. A
// refusal is never an Operation — it has no weight and no port edges — so a candidate that was considered and
// rejected is visible without being mistaken for one that survived. DOT is debug-only output (gated behind
// -Apercolate.debugGraphs), so this class writes the small statement/quoting subset of the format itself rather
// than pulling in a DOT-export library for it.
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
    private static final String REFUSAL_FILL = "#FFD6D6";

    // Renders scopeGraph captioned with caption; dimmed vertices render greyed and dashed.
    public String render(
            final Graph<GraphVertex, Dep> scopeGraph, final String caption, final Predicate<GraphVertex> dimmed) {
        return render(scopeGraph, caption, dimmed, false);
    }

    // As #render(Graph, String, Predicate), additionally drawing each Value's recorded refusals.
    public String render(
            final Graph<GraphVertex, Dep> scopeGraph,
            final String caption,
            final Predicate<GraphVertex> dimmed,
            final boolean withRefusals) {
        final var dot = new StringBuilder("digraph G {\n");
        final var labelLine = "  label=" + quote(caption) + ";\n";
        dot.append(labelLine);
        scopeGraph
                .vertexSet()
                .forEach(vertex ->
                        appendStatement(dot, quote(vertex.id()), vertexAttributes(vertex, dimmed.test(vertex))));
        scopeGraph.edgeSet().forEach(dep -> {
            final var head = quote(scopeGraph.getEdgeSource(dep).id()) + " -> "
                    + quote(scopeGraph.getEdgeTarget(dep).id());
            appendStatement(dot, head, edgeAttributes(dep));
        });
        if (withRefusals) {
            scopeGraph.vertexSet().forEach(vertex -> appendRefusals(dot, vertex));
        }
        dot.append('}');
        return dot.toString();
    }

    // Draws vertex's refusals, if it is a Value carrying any, in the order they were recorded. The renderer treats
    // each message as opaque text and never classifies a refusal by where it came from.
    @VisibleForTesting
    void appendRefusals(final StringBuilder dot, final GraphVertex vertex) {
        if (!(vertex instanceof Value)) {
            return;
        }
        final var value = (Value) vertex;
        final var refusals = value.getInadmissible();
        for (var index = 0; index < refusals.size(); index++) {
            appendRefusal(dot, value, refusals.get(index), index);
        }
    }

    @VisibleForTesting
    void appendRefusal(final StringBuilder dot, final Value value, final Refusal refusal, final int index) {
        final var id = quote(value.id() + "#refused-" + index);
        appendStatement(dot, id, refusalAttributes(refusal));
        appendStatement(dot, id + " -> " + quote(value.id()), Map.of(STYLE, "dashed"));
    }

    @VisibleForTesting
    Map<String, String> refusalAttributes(final Refusal refusal) {
        final var attrs = new LinkedHashMap<String, String>();
        attrs.put(LABEL, refusal.getMessage());
        attrs.put("shape", "note");
        attrs.put(STYLE, "filled,dashed");
        attrs.put(FILL, REFUSAL_FILL);
        return attrs;
    }

    @VisibleForTesting
    void appendStatement(final StringBuilder dot, final String head, final Map<String, String> attrs) {
        final var bracket = attrs.isEmpty()
                ? ""
                : " ["
                        + attrs.entrySet().stream()
                                .map(entry -> entry.getKey() + "=" + quote(entry.getValue()))
                                .collect(joining(", "))
                        + ']';
        final var statementLine = "  " + head + bracket + ";\n";
        dot.append(statementLine);
    }

    @VisibleForTesting
    Map<String, String> vertexAttributes(final GraphVertex vertex, final boolean dimmed) {
        final var attrs = new LinkedHashMap<String, String>();
        if (vertex instanceof Operation) {
            final var operation = (Operation) vertex;
            attrs.put(LABEL, operation.getLabel() + " (" + operation.getWeight() + ")");
            attrs.put("shape", "box");
            attrs.put(STYLE, "filled");
            attrs.put(FILL, "#EEEEEE");
        } else {
            final var value = (Value) vertex;
            attrs.put(LABEL, valueLabel(value));
            attrs.put("shape", "ellipse");
            attrs.put(STYLE, "filled");
            attrs.put(FILL, fillColor(value));
        }
        if (dimmed) {
            attrs.put(STYLE, "filled,dashed");
            attrs.put(FILL, DIM_FILL);
        }
        return attrs;
    }

    @VisibleForTesting
    Map<String, String> edgeAttributes(final Dep dep) {
        final var attrs = new LinkedHashMap<String, String>();
        dep.getPortId().ifPresent(portId -> attrs.put(LABEL, portId));
        return attrs;
    }

    @VisibleForTesting
    String valueLabel(final Value value) {
        final var typeSegment = value.getType()
                .map(type -> formatType(type, value.getNullness().orElse(null)))
                .orElse(UNKNOWN_TYPE);
        return value.getLoc().segment() + "\\n" + typeSegment;
    }

    // The type rendered with simple names and a JSpecify ?/! mark at the top (reference) level, read from the
    // Value's own resolved nullness — the only nullness this renderer knows (design nullability, change decouple-
    // engine-from-strategy-semantics): nested type arguments carry no mark, since the graph records no resolved
    // nullness for them and this renderer re-derives none of its own. Non-declared kinds (primitives, arrays,
    // wildcards) fall back to their text form, unmarked.
    @VisibleForTesting
    String formatType(final TypeMirror type, final @Nullable Nullability topNullness) {
        if (type.getKind() != DECLARED) {
            return body(type);
        }
        return body(type) + topMark(topNullness);
    }

    @VisibleForTesting
    String body(final TypeMirror type) {
        if (type.getKind() != DECLARED) {
            return type.toString();
        }
        final var declared = (DeclaredType) type;
        final var name = declared.asElement().getSimpleName().toString();
        final var args = declared.getTypeArguments();
        if (args.isEmpty()) {
            return name;
        }
        final var inner = args.stream().map(this::body).collect(joining(", "));
        return name + '<' + inner + '>';
    }

    @VisibleForTesting
    String topMark(final @Nullable Nullability nullness) {
        if (nullness == NULLABLE) {
            return "?";
        }
        return nullness == NON_NULL ? "!" : "";
    }

    @VisibleForTesting
    String fillColor(final Value value) {
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

    @VisibleForTesting
    String quote(final String value) {
        final var escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
