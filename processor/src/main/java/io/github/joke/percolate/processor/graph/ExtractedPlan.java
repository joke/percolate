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
public final class ExtractedPlan {

    private static final double UNREACHABLE = Double.POSITIVE_INFINITY;

    private final MapperGraph graph;

    @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
    private final Map<Value, Operation> chosen = new IdentityHashMap<>();

    @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
    private final Map<Value, Double> valueCost = new IdentityHashMap<>();

    @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
    private final Map<Operation, Double> operationCost = new IdentityHashMap<>();

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

    private Optional<Operation> cheapestProducer(final Value value) {
        return graph.producersOf(value)
                .filter(graph::isSat)
                .min(Comparator.<Operation>comparingDouble(this::costOf).thenComparing(Operation::id));
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

    private boolean isSupplyRoot(final Value value) {
        return value.getLoc() instanceof SourceLocation || value.getLoc() instanceof ElementLocation;
    }
}
