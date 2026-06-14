package io.github.joke.percolate.processor.graph;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The read-only extracted plan (design D8): a single {@code chosenProducer} per in-plan {@link Value}, selected
 * by bottom-up cost recursion over the SAT subgraph — {@code cost(Value) = min} over its SAT producer
 * {@link Operation}s, {@code cost(Operation) = weight + Σ cost(port Values)} (plus the child return-root cost for
 * a scope-owning Operation). The cost is per-use (no shared-subexpression discount), so the recursion is exact;
 * ties break on {@link Operation#id()} for compilation-stable selection. Losing producers and their subgraphs
 * remain in the underlying graph, unselected — this view never mutates it.
 */
// IdentityHashMap is the point: every memo here is keyed by vertex instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
public final class ExtractedPlan {

    private static final double UNREACHABLE = Double.POSITIVE_INFINITY;
    private static final int UNREACHABLE_PARTIAL = Integer.MAX_VALUE / 2;

    private final MapperGraph graph;

    private final Map<Value, Operation> chosen = new IdentityHashMap<>();

    private final Map<Value, Double> valueCost = new IdentityHashMap<>();

    private final Map<Operation, Double> operationCost = new IdentityHashMap<>();

    private final Map<Value, Integer> valuePartial = new IdentityHashMap<>();

    private final Map<Operation, Integer> operationPartial = new IdentityHashMap<>();

    private ExtractedPlan(final MapperGraph graph) {
        this.graph = graph;
    }

    /** Extracts the plan, rooted at every SAT return-root Value. */
    public static ExtractedPlan extract(final MapperGraph graph) {
        final var plan = new ExtractedPlan(graph);
        graph.values()
                .filter(value -> value.getLoc().isReturnRoot())
                .filter(graph::isSat)
                .forEach(plan::walk);
        return plan;
    }

    /** The chosen producer of {@code value} in the plan, or empty when it is a leaf (a supply root). */
    public Optional<Operation> chosenProducer(final Value value) {
        return Optional.ofNullable(chosen.get(value));
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
     * The chosen producer of {@code value}: <b>totality dominance</b> first (design D8) — the producer whose
     * subtree contains the fewest <b>partial</b> operations ({@code unwrap}/{@code requireNonNull}, transitively
     * through ports and child plans) wins, independent of weight, so a runtime-throwing plan loses to an
     * equivalent total one (drop-empties over throwing unwrap; {@code [coalesce]} over {@code [requireNonNull]}).
     * A partial producer is chosen only when every alternative carries at least as much partiality. Cost and the
     * deterministic {@code id} tie-break decide only among producers of equal partiality.
     */
    private Optional<Operation> cheapestProducer(final Value value) {
        return graph.producersOf(value)
                .filter(graph::isSat)
                .min(Comparator.<Operation>comparingInt(this::partialOf)
                        .thenComparingDouble(this::costOf)
                        .thenComparing(Operation::id));
    }

    private double costOf(final Value value) {
        final var memo = valueCost.get(value);
        if (memo != null) {
            return memo;
        }
        valueCost.put(value, UNREACHABLE); // cycle guard: a not-yet-resolved value costs +inf
        final var cost = cheapestProducer(value).map(this::costOf).orElse(isSupplyRoot(value) ? 0.0 : UNREACHABLE);
        valueCost.put(value, cost);
        return cost;
    }

    private double costOf(final Operation operation) {
        final var memo = operationCost.get(operation);
        if (memo != null) {
            return memo;
        }
        operationCost.put(operation, UNREACHABLE);
        double cost = operation.getWeight();
        cost += graph.portSourcesOf(operation).mapToDouble(this::costOf).sum();
        cost += operation
                .getChildScope()
                .map(child -> costOf(child.getReturnRoot()))
                .orElse(0.0);
        operationCost.put(operation, cost);
        return cost;
    }

    /** Transitive partial-operation count through {@code value}'s chosen producer (totality metric). */
    private int partialOf(final Value value) {
        final var memo = valuePartial.get(value);
        if (memo != null) {
            return memo;
        }
        valuePartial.put(value, UNREACHABLE_PARTIAL); // cycle guard, mirroring cost
        final var partial =
                cheapestProducer(value).map(this::partialOf).orElse(isSupplyRoot(value) ? 0 : UNREACHABLE_PARTIAL);
        valuePartial.put(value, partial);
        return partial;
    }

    /** Partial-operation count over {@code operation}: itself if partial, plus its ports and child plan. */
    private int partialOf(final Operation operation) {
        final var memo = operationPartial.get(operation);
        if (memo != null) {
            return memo;
        }
        operationPartial.put(operation, UNREACHABLE_PARTIAL);
        var partial = operation.isPartial() ? 1 : 0;
        partial += graph.portSourcesOf(operation).mapToInt(this::partialOf).sum();
        partial += operation
                .getChildScope()
                .map(child -> partialOf(child.getReturnRoot()))
                .orElse(0);
        final var saturated = Math.min(partial, UNREACHABLE_PARTIAL);
        operationPartial.put(operation, saturated);
        return saturated;
    }

    private boolean isSupplyRoot(final Value value) {
        return value.getLoc() instanceof SourceLocation || value.getLoc() instanceof ElementLocation;
    }
}
