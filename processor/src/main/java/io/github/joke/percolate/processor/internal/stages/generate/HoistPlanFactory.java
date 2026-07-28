package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;

// Builds a HoistPlan — the reachability walk, the port-consumer tally, and the hoist predicate itself. Split
// from HoistPlan by change tighten-testability-conventions (design D2): the decision of which Values
// materialise as named locals is the logic worth testing, and as statics on the plan it could not be
// intercepted. HoistPlan keeps only what a built plan answers — isHoisted, naming, references.
// IdentityHashMap is the point: every memo here is keyed by Value/Operation instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@NoArgsConstructor(onConstructor_ = @Inject)
final class HoistPlanFactory {

    private static final int NARY = 2;

    // Builds the hoist decision for the plan reachable from root, descending into child scopes; reservedNames (the
    // method's parameter names) are pre-allocated so no local shadows a parameter.
    HoistPlan forMethod(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final Value root,
            final Collection<String> reservedNames) {
        final Set<Operation> inPlanOps = Collections.newSetFromMap(new IdentityHashMap<>());
        collectOps(graph, plan, root, inPlanOps, Collections.newSetFromMap(new IdentityHashMap<>()));

        final Map<Value, Integer> portConsumers = new IdentityHashMap<>();
        final Set<Value> feedsNary = collectPortConsumers(graph, inPlanOps, portConsumers);
        final Set<Value> hoisted = hoistedValues(plan, portConsumers, feedsNary);

        final var names = new NameAllocator();
        reservedNames.forEach(names::newName);
        return new HoistPlan(hoisted, names);
    }

    // Tallies how many in-plan ports consume each source, returning the subset feeding an n-ary operation.
    Set<Value> collectPortConsumers(
            final MapperGraph graph, final Set<Operation> inPlanOps, final Map<Value, Integer> portConsumers) {
        final Set<Value> feedsNary = Collections.newSetFromMap(new IdentityHashMap<>());
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
    Set<Value> hoistedValues(
            final ExtractedPlan plan, final Map<Value, Integer> portConsumers, final Set<Value> feedsNary) {
        final Set<Value> hoisted = Collections.newSetFromMap(new IdentityHashMap<>());
        portConsumers.forEach((value, count) -> {
            if (isHoistCandidate(plan, feedsNary, value, count)) {
                hoisted.add(value);
            }
        });
        return hoisted;
    }

    boolean isHoistCandidate(final ExtractedPlan plan, final Set<Value> feedsNary, final Value value, final int count) {
        return plan.chosenProducer(value).isPresent() && (feedsNary.contains(value) || count > 1);
    }

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
