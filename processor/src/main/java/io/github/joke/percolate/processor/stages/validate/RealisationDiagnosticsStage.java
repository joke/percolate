package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.ExtractedPlan;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Walks unsatisfied demands and reports the closest miss (design D11): for each return-root {@code Value} left
 * unreachable (infinite extraction cost), it descends the deepest unreachable port chain to the demand with no
 * producer and emits a "no plan" error naming it. A targeted earlier diagnostic (constant coercion failure, dead
 * default) already explains an unreachable binding, so once the mapper is scarred the generic message is suppressed.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class RealisationDiagnosticsStage implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null || diagnostics.hasErrorsFor(ctx.getMapperType())) {
            return;
        }
        final var plan = ExtractedPlan.extract(graph);
        graph.values()
                .filter(value -> value.getScope() instanceof MethodScope)
                .filter(value -> value.getLoc().isReturnRoot())
                .filter(value -> !plan.reachable(value))
                .forEach(root -> emit(graph, plan, root, ctx));
    }

    private void emit(final MapperGraph graph, final ExtractedPlan plan, final Value root, final MapperContext ctx) {
        final var miss = deepestMiss(graph, plan, root);
        diagnostics.error(
                ctx.getMapperType(),
                String.format(
                        "no plan for %s: %s has no producer in the graph. Likely missing: a @Map-annotated method whose source produces %s",
                        label(root), label(miss), typeName(miss)));
    }

    /** Descends the first unreachable port chain from {@code value} to the demand with no reachable producer. */
    private static Value deepestMiss(final MapperGraph graph, final ExtractedPlan plan, final Value value) {
        final Set<Value> visited = new HashSet<>();
        var current = value;
        while (visited.add(current)) {
            final var producer =
                    graph.producersOf(current).filter(op -> !plan.reachable(op)).findFirst();
            if (producer.isEmpty()) {
                return current;
            }
            final var unsatPort = firstUnsatisfiedPort(graph, plan, producer.get());
            if (unsatPort.isEmpty()) {
                return current;
            }
            current = unsatPort.get();
        }
        return current;
    }

    private static Optional<Value> firstUnsatisfiedPort(
            final MapperGraph graph, final ExtractedPlan plan, final Operation operation) {
        return graph.portSourcesOf(operation)
                .filter(source -> !plan.reachable(source))
                .findFirst();
    }

    private static String label(final Value value) {
        if (value.getLoc() instanceof TargetLocation) {
            return "tgt[" + ((TargetLocation) value.getLoc()).getPath() + "]";
        }
        return value.id();
    }

    private static String typeName(final Value value) {
        return value.getType().map(TypeMirror::toString).orElse("?");
    }
}
