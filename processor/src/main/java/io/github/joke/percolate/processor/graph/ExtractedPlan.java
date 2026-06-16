package io.github.joke.percolate.processor.graph;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The read-only extracted plan (design D1/D8): a single {@code chosenProducer} per in-plan {@link Value}, selected
 * by one bottom-up minimum-cost-hyperpath fold over the bipartite graph. {@link Cost} is the lexicographic vector
 * {@code (partials, weight)}: {@code cost(Value)} is the {@code min} ({@code ⊕}) over its producers, and
 * {@code cost(Operation)} is its own {@code Cost} combined ({@code ⊗}, {@link Cost#plus}) with the sum over its
 * port Values and the child return-root. Totality therefore dominates weight by construction, and a partial
 * producer is chosen only when no total one is reachable; ties break on {@link Operation#id()} for compilation-
 * stable selection. The one fold subsumes satisfaction — a vertex is reachable iff its cost is finite (there is
 * no separate SAT pass). Losing producers remain in the underlying graph, unselected; this view never mutates it.
 */
// IdentityHashMap is the point: every memo here is keyed by vertex instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
public final class ExtractedPlan {

    private final MapperGraph graph;

    private final Map<Value, Operation> chosen = new IdentityHashMap<>();

    private final Map<Value, Cost> valueCost = new IdentityHashMap<>();

    private final Map<Operation, Cost> operationCost = new IdentityHashMap<>();

    private ExtractedPlan(final MapperGraph graph) {
        this.graph = graph;
    }

    /** Extracts the plan, rooted at every reachable return-root Value. */
    public static ExtractedPlan extract(final MapperGraph graph) {
        final var plan = new ExtractedPlan(graph);
        graph.values()
                .filter(value -> value.getLoc().isReturnRoot())
                .filter(plan::reachable)
                .forEach(plan::walk);
        return plan;
    }

    /** The chosen producer of {@code value} in the plan, or empty when it is a leaf (a supply root). */
    public Optional<Operation> chosenProducer(final Value value) {
        return Optional.ofNullable(chosen.get(value));
    }

    /** Whether {@code vertex} is producible: its extraction {@link Cost} is finite. Replaces the stored SAT bit. */
    public boolean reachable(final GraphVertex vertex) {
        return costOf(vertex).isReachable();
    }

    /** The extraction {@link Cost} of {@code value} (finite ⇒ reachable). */
    public Cost cost(final Value value) {
        final var memo = valueCost.get(value);
        if (memo != null) {
            return memo;
        }
        valueCost.put(value, Cost.INFINITE); // cycle guard: a not-yet-resolved value is unreachable
        final var cost =
                cheapestProducer(value).map(this::cost).orElseGet(() -> isBaseCase(value) ? Cost.ZERO : Cost.INFINITE);
        valueCost.put(value, cost);
        return cost;
    }

    private Cost costOf(final GraphVertex vertex) {
        return vertex instanceof Value ? cost((Value) vertex) : cost((Operation) vertex);
    }

    private void walk(final Value value) {
        if (chosen.containsKey(value)) {
            return;
        }
        final var producer = cheapestProducer(value);
        if (producer.isEmpty()) {
            return;
        }
        chosen.put(value, producer.get());
        graph.portSourcesOf(producer.get()).forEach(this::walk);
        producer.get().getChildScope().ifPresent(child -> walk(child.getReturnRoot()));
    }

    /**
     * The chosen producer of {@code value}: the reachable producer of least {@link Cost} (totality dominating
     * weight by the vector order), with {@link Operation#id()} the deterministic tie-break. Empty when the value
     * has no reachable producer (so an all-unreachable Value falls back to its base case in {@link #cost}).
     */
    private Optional<Operation> cheapestProducer(final Value value) {
        return graph.producersOf(value)
                .filter(operation -> cost(operation).isReachable())
                .min(Comparator.<Operation, Cost>comparing(this::cost).thenComparing(Operation::id));
    }

    private Cost cost(final Operation operation) {
        final var memo = operationCost.get(operation);
        if (memo != null) {
            return memo;
        }
        operationCost.put(operation, Cost.INFINITE);
        final var own = Cost.finite(operation.isPartial() ? 1 : 0, operation.getWeight());
        final var ports = graph.portSourcesOf(operation).map(this::cost).reduce(Cost.ZERO, Cost::plus);
        final var child = operation
                .getChildScope()
                .map(scope -> cost(scope.getReturnRoot()))
                .orElse(Cost.ZERO);
        final var cost = own.plus(ports).plus(child);
        operationCost.put(operation, cost);
        return cost;
    }

    /**
     * A producerless Value is a base case (cost {@link Cost#ZERO}) only when it is a {@code LEAF} — a parameter
     * root or a container element root. Every other producerless Value is unreachable ({@link Cost#INFINITE}),
     * including a multi-segment {@code ACCESS} source demand whose accessor never matched.
     */
    private boolean isBaseCase(final Value value) {
        return value.getLoc().role() == Location.Role.LEAF;
    }
}
