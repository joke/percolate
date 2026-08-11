package io.github.joke.percolate.processor.internal.graph;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static io.github.joke.percolate.processor.internal.graph.Cost.INFINITE;
import static io.github.joke.percolate.processor.internal.graph.Cost.ZERO;
import static io.github.joke.percolate.processor.internal.graph.Cost.finite;
import static io.github.joke.percolate.processor.internal.graph.Location.Role.LEAF;

// The read-only extracted plan (design D1/D8): a single chosenProducer per in-plan Value, selected by one
// bottom-up minimum-cost-hyperpath fold over the bipartite graph. Cost is the lexicographic vector (partials,
// weight): cost(Value) is the min (⊕) over its producers, and cost(Operation) is its own Cost combined (⊗,
// Cost.plus) with the sum over its port Values and the child return-root. Totality therefore dominates weight
// by construction, and a partial producer is chosen only when no total one is reachable; ties break on the
// graph-assigned seq (creation order), compared numerically — never on Operation.id(), whose seq substring
// compares lexicographically and silently inverts across a digit-count boundary (e.g. "op9" > "op10") — for
// compilation-stable selection. The one fold subsumes satisfaction — a vertex is reachable iff its cost is
// finite (there is no separate SAT pass). Losing producers remain in the underlying graph, unselected; this
// view never mutates it.
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

    // Extracts the plan, rooted at every reachable seeded method return root (the graph's recorded roots, not every
    // Value at the empty-path location). Child element plans flow from there through .walk's child-scope recursion,
    // so a same-location conversion way-point (a Stream<E> minted while producing a List<E> root) participates only
    // as a producer's port, never as an independent root.
    public static ExtractedPlan extract(final MapperGraph graph) {
        final var plan = new ExtractedPlan(graph);
        graph.returnRoots().filter(plan::reachable).forEach(plan::walk);
        return plan;
    }

    // The chosen producer of value in the plan, or empty when it is a leaf (a supply root).
    public Optional<Operation> chosenProducer(final Value value) {
        return Optional.ofNullable(chosen.get(value));
    }

    // Whether vertex is producible: its extraction Cost is finite. Replaces the stored SAT bit.
    public boolean reachable(final GraphVertex vertex) {
        return costOf(vertex).isReachable();
    }

    // The extraction Cost of value (finite ⇒ reachable).
    public Cost cost(final Value value) {
        final var memo = valueCost.get(value);
        if (memo != null) {
            return memo;
        }
        valueCost.put(value, INFINITE); // cycle guard: a not-yet-resolved value is unreachable
        final var cost = cheapestProducer(value).map(this::cost).orElseGet(() -> isBaseCase(value) ? ZERO : INFINITE);
        valueCost.put(value, cost);
        return cost;
    }

    Cost costOf(final GraphVertex vertex) {
        return vertex instanceof Value ? cost((Value) vertex) : cost((Operation) vertex);
    }

    void walk(final Value value) {
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

    // The chosen producer of value: the reachable producer of least Cost (totality dominating weight by the vector
    // order), with Operation.getSeq() the deterministic, numeric tie-break. Empty when the value has no reachable
    // producer (so an all-unreachable Value falls back to its base case in .cost).
    // Comparator.comparing needs an explicit type witness here, which a static import cannot carry.
    @SuppressWarnings("PMD.UseStaticImports")
    Optional<Operation> cheapestProducer(final Value value) {
        return graph.producersOf(value)
                .filter(operation -> cost(operation).isReachable())
                .min(Comparator.<Operation, Cost>comparing(this::cost).thenComparingInt(Operation::getSeq));
    }

    Cost cost(final Operation operation) {
        final var memo = operationCost.get(operation);
        if (memo != null) {
            return memo;
        }
        operationCost.put(operation, INFINITE);
        final var own = finite(operation.isPartial() ? 1 : 0, operation.getWeight());
        final var ports = graph.portSourcesOf(operation).map(this::cost).reduce(ZERO, Cost::plus);
        final var child = operation
                .getChildScope()
                .map(scope -> cost(scope.getReturnRoot()))
                .orElse(ZERO);
        final var cost = own.plus(ports).plus(child);
        operationCost.put(operation, cost);
        return cost;
    }

    // A producerless Value is a base case (cost ZERO) only when it is a LEAF — a parameter root or a container
    // element root. Every other producerless Value is unreachable (INFINITE), including a multi-segment ACCESS
    // source demand whose accessor never matched.
    boolean isBaseCase(final Value value) {
        return value.getLoc().role() == LEAF;
    }
}
