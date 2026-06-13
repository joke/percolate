package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.MapperGraph;
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
 * UNSAT after Horn propagation, it descends the deepest unsatisfied port chain to the demand with no producer and
 * emits a "no plan" error naming it. A targeted earlier diagnostic (constant coercion failure, dead default)
 * already explains an UNSAT binding, so once the mapper is scarred the generic message is suppressed.
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
        graph.values()
                .filter(value -> value.getLoc().isReturnRoot())
                .filter(value -> !graph.isSat(value))
                .forEach(root -> emit(graph, root, ctx));
    }

    private void emit(final MapperGraph graph, final Value root, final MapperContext ctx) {
        final var miss = deepestMiss(graph, root);
        diagnostics.error(
                ctx.getMapperType(),
                String.format(
                        "no plan for %s: %s has no producer in the graph. Likely missing: a @Map-annotated method whose source produces %s",
                        label(root), label(miss), typeName(miss)));
    }

    /** Descends the first unsatisfied port chain from {@code value} to the demand with no satisfiable producer. */
    private static Value deepestMiss(final MapperGraph graph, final Value value) {
        final Set<Value> visited = new HashSet<>();
        var current = value;
        while (visited.add(current)) {
            final var producer =
                    graph.producersOf(current).filter(op -> !graph.isSat(op)).findFirst();
            if (producer.isEmpty()) {
                return current;
            }
            final var unsatPort = firstUnsatisfiedPort(graph, producer.get());
            if (unsatPort.isEmpty()) {
                return current;
            }
            current = unsatPort.get();
        }
        return current;
    }

    private static Optional<Value> firstUnsatisfiedPort(final MapperGraph graph, final Operation operation) {
        return graph.portSourcesOf(operation)
                .filter(source -> !graph.isSat(source))
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
