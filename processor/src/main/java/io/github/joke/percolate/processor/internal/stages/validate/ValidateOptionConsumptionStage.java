package io.github.joke.percolate.processor.internal.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
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
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The consumption-tracked directive-option rail's diagnostic half (design D3 of change
 * {@code add-temporal-type-mapping}): after expansion, for every binding whose {@code @Map} directive declares an
 * option ({@code format}, {@code zone}), computes {@code declared − consumed} where {@code consumed} is the union
 * of {@link Operation#getConsumedOptionKeys()} stamped over the <strong>winning</strong> plan reachable from that
 * binding's target {@link Value}. Every leftover key is reported as a compile error at the directive's source
 * position — the option was declared but had no effect on the generated code. A strategy that read no option
 * stamps nothing, so an option consumed by a non-winning candidate still diagnoses (design decision: reflects what
 * the generated code actually does, not what was merely attempted). This is a read-only pass over the plan
 * already extracted for {@link io.github.joke.percolate.processor.internal.stages.generate.GenerateStage}; it
 * mutates neither the graph nor the plan (no engine-core change).
 */
@SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded validation pass; no concurrent access
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateOptionConsumptionStage implements Stage {

    static final String FORMAT_KEY = "format";
    static final String ZONE_KEY = "zone";

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        final var graph = ctx.getGraph();
        if (mappings == null || graph == null) {
            return;
        }
        final var plan = ExtractedPlan.extract(graph);
        mappings.getMethods().forEach(method -> checkMethod(method, graph, plan));
    }

    private void checkMethod(final MethodMappings method, final MapperGraph graph, final ExtractedPlan plan) {
        final var scope = new MethodScope(method.getMethod());
        method.getDirectives().forEach(directive -> checkDirective(directive, method.getMethod(), scope, graph, plan));
    }

    private void checkDirective(
            final MappingDirective directive,
            final ExecutableElement method,
            final MethodScope scope,
            final MapperGraph graph,
            final ExtractedPlan plan) {
        final var declared = declaredOptions(directive);
        if (declared.isEmpty()) {
            return;
        }
        final var target = targetValue(graph, scope, directive.getTarget());
        final var consumed = target == null ? Set.<String>of() : consumedOptionKeys(graph, plan, target);
        declared.forEach((key, value) -> {
            if (!consumed.contains(key)) {
                diagnostics.error(
                        method,
                        directive.getMirror(),
                        value,
                        "@Map '" + key + "' has no effect on '" + directive.getTarget()
                                + "': no production in the winning plan consumed it");
            }
        });
    }

    /** The directive's declared option keys, each mapped to the {@link AnnotationValue} to position a diagnostic at. */
    private static Map<String, AnnotationValue> declaredOptions(final MappingDirective directive) {
        final Map<String, AnnotationValue> declared = new LinkedHashMap<>();
        if (directive.hasFormat()) {
            declared.put(FORMAT_KEY, directive.getFormatValue());
        }
        if (directive.hasZone()) {
            declared.put(ZONE_KEY, directive.getZoneValue());
        }
        return declared;
    }

    /** The consumed-option-key union over every {@link Operation} the winning plan reaches from {@code target}. */
    private static Set<String> consumedOptionKeys(
            final MapperGraph graph, final ExtractedPlan plan, final Value target) {
        final Set<Operation> ops = new HashSet<>();
        collectWinningOps(graph, plan, target, ops, newSeenSet());
        final Set<String> keys = new HashSet<>();
        ops.forEach(op -> keys.addAll(op.getConsumedOptionKeys()));
        return keys;
    }

    private static void collectWinningOps(
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
    private static Set<Value> newSeenSet() {
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
    private static Value targetValue(final MapperGraph graph, final MethodScope scope, final String target) {
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

    private static List<String> splitPath(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
