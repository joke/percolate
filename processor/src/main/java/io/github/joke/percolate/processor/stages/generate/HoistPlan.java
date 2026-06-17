package io.github.joke.percolate.processor.stages.generate;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.ExtractedPlan;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.Value;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The separable, pure hoist decision plus variable naming for one method body (design D1/D2). Given the
 * {@link ExtractedPlan} reachable from a method return-root, it decides which in-plan {@link Value}s materialise
 * as named locals: a Value with a chosen producer that either feeds a port of an <b>n-ary</b> {@link Operation}
 * ({@code getPorts().size() >= 2} — a multi-argument assembly call) <b>or</b> is consumed by more than one
 * in-plan port (so it is evaluated once, not re-rendered per use). Single-port chains (container
 * {@code iterate}/{@code collect}/{@code flatMap}/{@code wrap}/{@code unwrap}, conversions, accessors, nullness
 * crossings) and bare leaves (parameter / element-lambda roots, which have no chosen producer) stay inline.
 *
 * <p>It mutates neither the {@link MapperGraph} nor the {@link ExtractedPlan} and adds no codegen IR — it is the
 * seam toward a future per-scope binding schedule. Variable naming extends the existing {@code vN} counter and
 * the {@code Value -> reference CodeBlock} lookup lives here too, so the rendering decision and the naming sit in
 * one separable place. The counter is shared with the {@code Walk}'s lambda parameters so names never collide.
 */
// IdentityHashMap is the point: every memo here is keyed by Value/Operation instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class HoistPlan {

    private static final int NARY = 2;

    private final Set<Value> hoisted;

    private final Map<Value, CodeBlock> references = new IdentityHashMap<>();

    private int nextVar;

    /** Builds the hoist decision for the plan reachable from {@code root}, descending into child scopes. */
    static HoistPlan forRoot(final MapperGraph graph, final ExtractedPlan plan, final Value root) {
        final Set<Operation> inPlanOps = Collections.newSetFromMap(new IdentityHashMap<>());
        collectOps(graph, plan, root, inPlanOps, Collections.newSetFromMap(new IdentityHashMap<>()));

        final Set<Value> feedsNary = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Value, Integer> portConsumers = new IdentityHashMap<>();
        for (final var operation : inPlanOps) {
            final var nary = operation.getPorts().size() >= NARY;
            graph.portSourcesOf(operation).forEach(source -> {
                portConsumers.merge(source, 1, Integer::sum);
                if (nary) {
                    feedsNary.add(source);
                }
            });
        }

        final Set<Value> hoisted = Collections.newSetFromMap(new IdentityHashMap<>());
        portConsumers.forEach((value, count) -> {
            if (plan.chosenProducer(value).isPresent() && (feedsNary.contains(value) || count > 1)) {
                hoisted.add(value);
            }
        });
        return new HoistPlan(hoisted);
    }

    private static void collectOps(
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
            graph.portSourcesOf(producer).forEach(source -> collectOps(graph, plan, source, ops, seen));
            producer.getChildScope().ifPresent(child -> collectOps(graph, plan, child.getReturnRoot(), ops, seen));
        });
    }

    boolean isHoisted(final Value value) {
        return hoisted.contains(value);
    }

    /** Allocates a fresh {@code vN} name for {@code value}, records its reference, and returns the name. */
    String declare(final Value value) {
        final var name = freshVar();
        references.put(value, CodeBlock.of("$N", name));
        return name;
    }

    /** The variable reference of a hoisted, already-declared {@code value}. */
    CodeBlock reference(final Value value) {
        final var ref = references.get(value);
        if (ref == null) {
            throw new IllegalStateException("hoisted Value referenced before declaration: " + value.id());
        }
        return ref;
    }

    /** The next {@code vN} name; shared by hoisted locals and lambda parameters so names never collide. */
    String freshVar() {
        final var current = nextVar;
        nextVar++;
        return "v" + current;
    }
}
