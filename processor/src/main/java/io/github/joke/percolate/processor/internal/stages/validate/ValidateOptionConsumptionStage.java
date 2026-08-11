package io.github.joke.percolate.processor.internal.stages.validate;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MethodDirectives;
import io.github.joke.percolate.spi.DirectiveInput;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.processor.internal.graph.ExtractedPlan.extract;
import static java.util.Collections.newSetFromMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

// The consumption-tracked directive-option rail's diagnostic half (design D3/D7 of change decouple-engine-from-
// strategy-semantics): after expansion, for every explicitly bound target path (one a reader called bind at),
// computes declared − consumed where consumed is the union of Operation.getConsumed() stamped over the winning
// plan reachable from that path's target Value. Every leftover input is reported as a permanent compile error
// at its own DirectiveInput.getSubject() — the input was declared but had no effect on the generated code. A
// strategy that read no input stamps nothing, so an input consumed by a non-winning candidate still diagnoses
// (design decision: reflects what the generated code actually does, not what was merely attempted). An input-
// only path with no bind (e.g. a @MapEnum table, attached only at the empty root) is deliberately out of scope:
// when the sole strategy for that demand is refused outright (design D6's bound), nothing ever consumes
// anything and this rail would otherwise report a spurious "no effect" ahead of — and, per
// RealisationDiagnosticsStage's once-erred guard, in place of — the strategy's own, more specific refusal
// message. This is a read-only pass over the plan already extracted for
// io.github.joke.percolate.processor.internal.stages.generate.GenerateStage; it mutates neither the graph nor
// the plan (no engine-core change), and names no annotation.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateOptionConsumptionStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var methodDirectives = ctx.getMethodDirectives();
        final var graph = ctx.getGraph();
        if (methodDirectives == null || graph == null) {
            return;
        }
        final var plan = extract(graph);
        methodDirectives.forEach(directives -> checkMethod(directives, graph, plan, ctx));
    }

    void checkMethod(
            final MethodDirectives directives,
            final MapperGraph graph,
            final ExtractedPlan plan,
            final MapperContext ctx) {
        final var scope = new MethodScope(directives.getMethod());
        final var boundPaths = directives.getBinds().stream()
                .map(bind -> String.join(".", bind.getTargetPath()))
                .collect(toUnmodifiableSet());
        directives.getInputsByTarget().entrySet().stream()
                .filter(entry -> boundPaths.contains(entry.getKey()))
                .forEach(entry -> checkPath(entry.getKey(), entry.getValue(), scope, graph, plan, ctx));
    }

    void checkPath(
            final String path,
            final List<DirectiveInput> declared,
            final MethodScope scope,
            final MapperGraph graph,
            final ExtractedPlan plan,
            final MapperContext ctx) {
        if (declared.isEmpty()) {
            return;
        }
        final var target = targetValue(graph, scope, path);
        final var consumed = target == null ? Set.<DirectiveInput>of() : consumedInputs(graph, plan, target);
        declared.stream()
                .filter(input -> !consumed.contains(input))
                .forEach(input -> ctx.report(Diagnostic.error(
                                input.getSubject(),
                                "'" + input.getKey() + "' has no effect on '" + path
                                        + "': no production in the winning plan consumed it")
                        .asPermanent()));
    }

    // The consumed-input union over every Operation the winning plan reaches from target.
    Set<DirectiveInput> consumedInputs(final MapperGraph graph, final ExtractedPlan plan, final Value target) {
        final var ops = new HashSet<Operation>();
        collectWinningOps(graph, plan, target, ops, newSeenSet());
        final var inputs = new HashSet<DirectiveInput>();
        ops.forEach(op -> inputs.addAll(op.getConsumed()));
        return inputs;
    }

    void collectWinningOps(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final Value value,
            final Set<Operation> ops,
            final Set<Value> seen) {
        if (!seen.add(value)) {
            return;
        }
        plan.chosenProducer(value).ifPresent(producer -> {
            ops.add(producer);
            graph.portSourcesOf(producer).forEach(source -> collectWinningOps(graph, plan, source, ops, seen));
            producer.getChildScope()
                    .ifPresent(child -> collectWinningOps(graph, plan, child.getReturnRoot(), ops, seen));
        });
    }

    @SuppressWarnings("IdentityHashMapUsage")
    Set<Value> newSeenSet() {
        return newSetFromMap(new IdentityHashMap<>());
    }

    // The target Value at the end of target's dotted path, walked from the method's assembly root. The base case is
    // the graph-recorded MapperGraph.returnRootIn(Scope) — never a location-only lookup — because the engine may
    // over-emit a same-located conversion intermediate at the empty root TargetLocation (e.g. a String intermediate
    // en route to a format-configured target), and a location-only match could resolve to that intermediate instead
    // of the declared return type.
    @Nullable
    Value targetValue(final MapperGraph graph, final MethodScope scope, final String target) {
        var current = graph.returnRootIn(scope);
        for (final var segment : splitPath(target)) {
            final var declared = current;
            final var next = graph.producersOf(declared)
                    .map(op -> graph.portSource(op, segment))
                    .flatMap(Optional::stream)
                    .filter(value -> value.getType().isPresent())
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    List<String> splitPath(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
