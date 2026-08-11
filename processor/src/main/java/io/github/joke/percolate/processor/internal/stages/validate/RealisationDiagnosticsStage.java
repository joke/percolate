package io.github.joke.percolate.processor.internal.stages.validate;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

import static io.github.joke.percolate.processor.Diagnostic.error;
import static io.github.joke.percolate.processor.internal.graph.ExtractedPlan.extract;
import static io.github.joke.percolate.spi.Subjects.none;
import static java.lang.String.format;

// Walks unsatisfied demands and records the closest miss (design D11): for each return-root Value left
// unreachable (infinite extraction cost), it descends the deepest unreachable port chain to the demand with no
// producer. When that demand carries Value#getInadmissible() refusals (design D2 of change decouple-engine-
// from-strategy-semantics), each is reported at its own Subject in place of the generic message, deduplicated
// only when byte-identical; otherwise the generic transient "no plan" Diagnostic is recorded (design D14) —
// transient because a later round (e.g. Lombok interop) may still realise the mapper. A targeted earlier
// diagnostic already explains an unreachable binding, so once the mapper already has an error nothing is
// recorded here.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class RealisationDiagnosticsStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null || ctx.hasErrors()) {
            return;
        }
        final var plan = extract(graph);
        graph.returnRoots().filter(root -> !plan.reachable(root)).forEach(root -> report(ctx, graph, plan, root));
    }

    void report(final MapperContext ctx, final MapperGraph graph, final ExtractedPlan plan, final Value root) {
        final var miss = deepestMiss(graph, plan, root);
        final var refusals = miss.getInadmissible();
        if (refusals.isEmpty()) {
            ctx.report(error(none(), genericMessage(root, miss)));
            return;
        }
        final var reported = new LinkedHashSet<String>();
        refusals.stream()
                .filter(refusal -> reported.add(refusal.getMessage()))
                .forEach(refusal -> ctx.report(error(refusal.getSubject(), refusal.getMessage())));
    }

    String genericMessage(final Value root, final Value miss) {
        return format(
                "no plan for %s: %s has no producer in the graph. Likely missing: a @Map-annotated method whose source produces %s",
                label(root), label(miss), typeName(miss));
    }

    // Descends the first unreachable port chain from value to the demand with no reachable producer.
    Value deepestMiss(final MapperGraph graph, final ExtractedPlan plan, final Value value) {
        final var visited = new HashSet<Value>();
        var current = value;
        while (visited.add(current)) {
            final var next = nextUnsatisfied(graph, plan, current);
            if (next.isEmpty()) {
                return current;
            }
            current = next.get();
        }
        return current;
    }

    // The unsatisfied port feeding current's unreachable producer, or empty when current is the miss.
    Optional<Value> nextUnsatisfied(final MapperGraph graph, final ExtractedPlan plan, final Value current) {
        final var producer =
                graph.producersOf(current).filter(op -> !plan.reachable(op)).findFirst();
        return producer.isEmpty() ? Optional.empty() : firstUnsatisfiedPort(graph, plan, producer.get());
    }

    Optional<Value> firstUnsatisfiedPort(final MapperGraph graph, final ExtractedPlan plan, final Operation operation) {
        return graph.portSourcesOf(operation)
                .filter(source -> !plan.reachable(source))
                .findFirst();
    }

    String label(final Value value) {
        if (value.getLoc() instanceof TargetLocation) {
            return "tgt[" + ((TargetLocation) value.getLoc()).getPath() + "]";
        }
        return value.id();
    }

    String typeName(final Value value) {
        return value.getType().map(TypeMirror::toString).orElse("?");
    }
}
