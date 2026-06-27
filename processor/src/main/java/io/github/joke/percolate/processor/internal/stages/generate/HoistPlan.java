package io.github.joke.percolate.processor.internal.stages.generate;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.NameAllocator;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The separable, pure hoist decision plus variable naming for one method body (design D1/D2/D5). Given the
 * {@link ExtractedPlan} reachable from a method return-root, it decides which in-plan {@link Value}s materialise
 * as named locals: a Value with a chosen producer that either feeds a port of an <b>n-ary</b> {@link Operation}
 * ({@code getPorts().size() >= 2} — a multi-argument assembly call) <b>or</b> is consumed by more than one
 * in-plan port (so it is evaluated once, not re-rendered per use). Single-port chains (container
 * {@code iterate}/{@code collect}/{@code flatMap}/{@code wrap}/{@code unwrap}, conversions, accessors, nullness
 * crossings) and bare leaves (parameter / element-lambda roots, which have no chosen producer) stay inline.
 *
 * <p>It mutates neither the {@link MapperGraph} nor the {@link ExtractedPlan} and adds no codegen IR — it is the
 * seam toward a future per-scope binding schedule. Naming lives here too: each hoisted local is named after the
 * slot it materialises ({@code Location.slotName()} — the target field, source segment, or element role) and a
 * lambda parameter after its element type, made unique within the method by a {@link NameAllocator} seeded with
 * the method's parameter names (so a local never shadows a parameter, collisions get a suffix, and reserved words
 * are sanitised).
 */
// IdentityHashMap is the point: every memo here is keyed by Value/Operation instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class HoistPlan {

    private static final int NARY = 2;

    private final Set<Value> hoisted;

    private final NameAllocator names;

    private final Map<Value, CodeBlock> references = new IdentityHashMap<>();

    /**
     * Builds the hoist decision for the plan reachable from {@code root}, descending into child scopes;
     * {@code reservedNames} (the method's parameter names) are pre-allocated so no local shadows a parameter.
     */
    static HoistPlan forMethod(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final Value root,
            final Collection<String> reservedNames) {
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

        final var names = new NameAllocator();
        reservedNames.forEach(names::newName);
        return new HoistPlan(hoisted, names);
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

    /** Allocates a unique name for a hoisted {@code value} from its slot name, records its reference, returns it. */
    String declare(final Value value) {
        final var name = names.newName(slotBase(value));
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

    /** Allocates a unique lambda-parameter name for an element of {@code elementType} (from the child input decl). */
    String lambdaName(final TypeMirror elementType) {
        return names.newName(typeBase(elementType));
    }

    private static String slotBase(final Value value) {
        final var slot = value.getLoc().slotName();
        return slot.isEmpty() ? "value" : slot;
    }

    private static String typeBase(final TypeMirror type) {
        if (type instanceof DeclaredType) {
            final var simple = ((DeclaredType) type).asElement().getSimpleName().toString();
            if (!simple.isEmpty()) {
                return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
            }
        }
        return "element";
    }
}
