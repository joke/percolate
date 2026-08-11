package io.github.joke.percolate.processor.internal.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.GraphVertex;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.processor.internal.graph.ExtractedPlan.extract;
import static java.util.Collections.newSetFromMap;

// Dumps the chosen-plan view (in-plan vertices only), one .plan.dot file per scope.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpPlanStage implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final var plan = extract(graph);
        final var inPlan = newSetFromMap(new IdentityHashMap<GraphVertex, Boolean>());
        graph.values().forEach(value -> collectInPlan(plan, value, inPlan));
        writer.dumpWithRefusals(ctx, "plan", inPlan::contains, false);
    }

    // Records value and its chosen producer as belonging to the plan slice; a value with no chosen producer
    // is not in the plan and neither vertex is recorded.
    @VisibleForTesting
    void collectInPlan(final ExtractedPlan plan, final Value value, final Set<GraphVertex> inPlan) {
        plan.chosenProducer(value).ifPresent(operation -> addBoth(inPlan, value, operation));
    }

    @VisibleForTesting
    void addBoth(final Set<GraphVertex> inPlan, final GraphVertex value, final GraphVertex operation) {
        inPlan.add(value);
        inPlan.add(operation);
    }
}
