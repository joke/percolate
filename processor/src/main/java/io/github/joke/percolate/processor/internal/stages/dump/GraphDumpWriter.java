package io.github.joke.percolate.processor.internal.stages.dump;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.internal.graph.Dep;
import io.github.joke.percolate.processor.internal.graph.DotRenderer;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.GraphVertex;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.Scope;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * Owns the debug-graph IO shared by the {@code Dump*} stages: the {@code debugGraphs} gate, the empty-graph skip,
 * the per-scope partition, the {@link DotRenderer} pass, and the {@link Filer} write. Each stage supplies a
 * vertex-inclusion predicate and a {@code <view>} infix; one file is written per scope as
 * {@code <MapperFQN>.<method>[-n].<view>.dot}.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class GraphDumpWriter {

    private final Filer filer;
    private final Diagnostics diagnostics;
    private final ProcessorOptions processorOptions;
    private final DotRenderer dotRenderer;

    public void dump(final MapperContext ctx, final String view, final Predicate<GraphVertex> include) {
        dump(ctx, view, include, false);
    }

    /**
     * As {@link #dump(MapperContext, String, Predicate)}, but when {@code dimUnreachable} is set the rendered
     * vertices that are unreachable (infinite extraction cost) are greyed/dashed rather than dropped — the full
     * dump's view of the surviving plan against the pruned over-emission.
     */
    public void dump(
            final MapperContext ctx,
            final String view,
            final Predicate<GraphVertex> include,
            final boolean dimUnreachable) {
        final var graph = ctx.getGraph();
        if (graph == null || skipDump(graph, ctx)) {
            return;
        }
        final Predicate<GraphVertex> dimmed = dimUnreachable ? dimmedByCost(graph) : vertex -> false;
        final var mapperType = ctx.getMapperType();
        final var fqn = mapperType.getQualifiedName().toString();
        final var infixes = infixes(orderedScopes(graph, include));
        infixes.forEach((scope, infix) -> writeScope(graph, include, dimmed, scope, infix, fqn, view, mapperType));
    }

    // Like GenerateStage, this stage writes through the Filer, which forbids reopening a path. The pipeline
    // re-runs every deferral round, so dump only on the round the mapper realises (empty outcome) — otherwise
    // a deferred-then-realised mapper would write each .dot twice.
    private boolean skipDump(final MapperGraph graph, final MapperContext ctx) {
        return !processorOptions.isDebugGraphs()
                || graph.vertexCount() == 0
                || !ctx.getUnsatisfiedRealisation().isEmpty();
    }

    private static Predicate<GraphVertex> dimmedByCost(final MapperGraph graph) {
        final var plan = ExtractedPlan.extract(graph);
        return vertex -> !plan.reachable(vertex);
    }

    private void writeScope(
            final MapperGraph graph,
            final Predicate<GraphVertex> include,
            final Predicate<GraphVertex> dimmed,
            final Scope scope,
            final String infix,
            final String fqn,
            final String view,
            final TypeElement mapperType) {
        final var dot = dotRenderer.render(slice(graph, scope, include), scope.encode(), dimmed);
        final var fileName = fqn + "." + infix + "." + view + ".dot";
        try {
            final var resource = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", fileName, mapperType);
            try (var os = resource.openOutputStream();
                    var writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(dot);
                writer.flush();
            }
        } catch (final IOException e) {
            diagnostics.warning(mapperType, "Failed to write " + view + " debug graph: " + e.getMessage());
        }
    }

    private static Graph<GraphVertex, Dep> slice(
            final MapperGraph graph, final Scope scope, final Predicate<GraphVertex> include) {
        final var slice = new DirectedMultigraph<GraphVertex, Dep>(Dep.class);
        graph.vertices()
                .filter(vertex -> vertex.getScope().equals(scope) && include.test(vertex))
                .forEach(slice::addVertex);
        graph.deps().forEach(dep -> {
            final var from = graph.getDepSource(dep);
            final var to = graph.getDepTarget(dep);
            if (slice.containsVertex(from) && slice.containsVertex(to)) {
                slice.addEdge(from, to, dep);
            }
        });
        return slice;
    }

    private static List<Scope> orderedScopes(final MapperGraph graph, final Predicate<GraphVertex> include) {
        return graph.vertices()
                .filter(include)
                .map(GraphVertex::getScope)
                .distinct()
                .sorted(Comparator.comparing(Scope::encode))
                .collect(toUnmodifiableList());
    }

    private static Map<Scope, String> infixes(final List<Scope> scopes) {
        final var byBase =
                scopes.stream().collect(groupingBy(GraphDumpWriter::baseInfix, LinkedHashMap::new, toList()));
        final var result = new LinkedHashMap<Scope, String>();
        byBase.forEach((base, group) -> result.putAll(infixesWithinGroup(base, group)));
        return result;
    }

    private static Map<Scope, String> infixesWithinGroup(final String base, final List<Scope> group) {
        final var disambiguate = group.size() > 1;
        final var result = new LinkedHashMap<Scope, String>();
        for (var index = 0; index < group.size(); index++) {
            result.put(group.get(index), disambiguate ? base + "-" + index : base);
        }
        return result;
    }

    private static String baseInfix(final Scope scope) {
        if (scope instanceof MethodScope) {
            return ((MethodScope) scope).getMethod().getSimpleName().toString();
        }
        // A child (element) scope's encode() nests its owning operation's id, which compounds to an
        // unwritable file name for deep container nesting. Group child scopes under their enclosing method;
        // infix disambiguation appends -0, -1, … so each scope still gets a distinct, short file name.
        return enclosingMethodInfix(scope) + "-elem";
    }

    private static String enclosingMethodInfix(final Scope scope) {
        var current = scope;
        while (!(current instanceof MethodScope)) {
            final var parent = current.parent();
            if (parent.isEmpty()) {
                return "scope";
            }
            current = parent.get();
        }
        return ((MethodScope) current).getMethod().getSimpleName().toString();
    }
}
