package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.Collections.newSetFromMap;

// Builds a HoistPlan — the reachability walk, the port-consumer tally, and the hoist predicate itself. Split
// from HoistPlan by change tighten-testability-conventions (design D2): the decision of which Values
// materialise as named locals is the logic worth testing, and as statics on the plan it could not be
// intercepted. HoistPlan keeps only what a built plan answers — isHoisted, naming, references.
// IdentityHashMap is the point: every memo here is keyed by Value/Operation instance identity, not value equality.
@SuppressWarnings("IdentityHashMapUsage")
@NoArgsConstructor(onConstructor_ = @Inject)
final class HoistPlanFactory {

    private static final int NARY = 2;

    // Builds the hoist decision for the plan reachable from root, descending into child scopes; reservedNames (the
    // method's parameter names) are pre-allocated so no local shadows a parameter.
    @VisibleForTesting
    HoistPlan forMethod(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final Value root,
            final Collection<String> reservedNames) {
        final var inPlanOps = newSetFromMap(new IdentityHashMap<Operation, Boolean>());
        collectOps(graph, plan, root, inPlanOps, newSetFromMap(new IdentityHashMap<>()));

        final var portConsumers = new IdentityHashMap<Value, Integer>();
        final var feedsNary = collectPortConsumers(graph, inPlanOps, portConsumers);
        final var hoisted = hoistedValues(plan, portConsumers, feedsNary);

        final var names = new NameAllocator();
        reservedNames.forEach(names::newName);
        return new HoistPlan(hoisted, names);
    }

    // Tallies how many in-plan ports consume each source, returning the subset feeding an n-ary operation.
    @VisibleForTesting
    Set<Value> collectPortConsumers(
            final MapperGraph graph, final Set<Operation> inPlanOps, final Map<Value, Integer> portConsumers) {
        final var feedsNary = newSetFromMap(new IdentityHashMap<Value, Boolean>());
        for (final var operation : inPlanOps) {
            final var nary = operation.getPorts().size() >= NARY;
            graph.portSourcesOf(operation).forEach(source -> {
                portConsumers.merge(source, 1, Integer::sum);
                if (nary) {
                    feedsNary.add(source);
                }
            });
        }
        return feedsNary;
    }

    // The Values that materialise as named locals: a chosen producer feeding an n-ary op or more than one port.
    @VisibleForTesting
    Set<Value> hoistedValues(
            final ExtractedPlan plan, final Map<Value, Integer> portConsumers, final Set<Value> feedsNary) {
        final var hoisted = newSetFromMap(new IdentityHashMap<Value, Boolean>());
        portConsumers.forEach((value, count) -> {
            if (isHoistCandidate(plan, feedsNary, value, count)) {
                hoisted.add(value);
            }
        });
        return hoisted;
    }

    @VisibleForTesting
    boolean isHoistCandidate(final ExtractedPlan plan, final Set<Value> feedsNary, final Value value, final int count) {
        return plan.chosenProducer(value).isPresent() && (feedsNary.contains(value) || count > 1);
    }

    @VisibleForTesting
    void collectOps(
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
}
