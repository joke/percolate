package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import io.github.joke.percolate.spi.DirectiveInput;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The consumption-tracked directive-option rail's diagnostic half (design D3 of change
 * {@code decouple-engine-from-strategy-semantics}): after expansion, for every binding whose {@code @Map}
 * directive declares an input (any key — {@code constant}, {@code defaultValue}, {@code format}, {@code zone}, …),
 * computes {@code declared − consumed} where {@code consumed} is the union of {@link Operation#getConsumed()}
 * stamped over the <strong>winning</strong> plan reachable from that binding's target {@link Value}. Every leftover
 * input is reported as a permanent compile error at its own {@link DirectiveInput#getSubject()} — the member was
 * declared but had no effect on the generated code. A strategy that read no input stamps nothing, so an input
 * consumed by a non-winning candidate still diagnoses (design decision: reflects what the generated code actually
 * does, not what was merely attempted). This is a read-only pass over the plan already extracted for
 * {@link io.github.joke.percolate.processor.internal.stages.generate.GenerateStage}; it mutates neither the graph
 * nor the plan (no engine-core change).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateOptionConsumptionStage implements Stage {

    private static final String SOURCE_KEY = "source";

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        final var graph = ctx.getGraph();
        if (mappings == null || graph == null) {
            return;
        }
        final var plan = ExtractedPlan.extract(graph);
        mappings.getMethods().forEach(method -> checkMethod(method, graph, plan, ctx));
    }

    void checkMethod(
            final MethodMappings method, final MapperGraph graph, final ExtractedPlan plan, final MapperContext ctx) {
        final var scope = new MethodScope(method.getMethod());
        method.getDirectives().forEach(directive -> checkDirective(directive, scope, graph, plan, ctx));
    }

    void checkDirective(
            final MappingDirective directive,
            final MethodScope scope,
            final MapperGraph graph,
            final ExtractedPlan plan,
            final MapperContext ctx) {
        final var declared = directive.getInputs().stream()
                .filter(input -> !SOURCE_KEY.equals(input.getKey()))
                .collect(toUnmodifiableList());
        if (declared.isEmpty()) {
            return;
        }
        final var target = targetValue(graph, scope, directive.getTarget());
        final var consumed = target == null ? Set.<DirectiveInput>of() : consumedInputs(graph, plan, target);
        declared.stream()
                .filter(input -> !consumed.contains(input))
                .forEach(input -> ctx.report(Diagnostic.error(
                                input.getSubject(),
                                "@Map '" + input.getKey() + "' has no effect on '" + directive.getTarget()
                                        + "': no production in the winning plan consumed it")
                        .asPermanent()));
    }

    /** The consumed-input union over every {@link Operation} the winning plan reaches from {@code target}. */
    static Set<DirectiveInput> consumedInputs(final MapperGraph graph, final ExtractedPlan plan, final Value target) {
        final Set<Operation> ops = new HashSet<>();
        collectWinningOps(graph, plan, target, ops, newSeenSet());
        final Set<DirectiveInput> inputs = new HashSet<>();
        ops.forEach(op -> inputs.addAll(op.getConsumed()));
        return inputs;
    }

    static void collectWinningOps(
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
    static Set<Value> newSeenSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * The target Value at the end of {@code target}'s dotted path, walked from the method's assembly root. The
     * base case is the graph-recorded {@link MapperGraph#returnRootIn(Scope)} — never a location-only lookup —
     * because the engine may over-emit a same-located conversion intermediate at the empty root {@link
     * TargetLocation} (e.g. a {@code String} intermediate en route to a {@code @Map(format=…)} target), and a
     * location-only match could resolve to that intermediate instead of the declared return type.
     */
    @Nullable
    static Value targetValue(final MapperGraph graph, final MethodScope scope, final String target) {
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

    static List<String> splitPath(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
