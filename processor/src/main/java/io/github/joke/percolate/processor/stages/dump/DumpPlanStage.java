package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.ExtractedPlan;
import io.github.joke.percolate.processor.graph.GraphVertex;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** Dumps the chosen-plan view (in-plan vertices only), one {@code .plan.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpPlanStage implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final var plan = ExtractedPlan.extract(graph);
        final Set<GraphVertex> inPlan = Collections.newSetFromMap(new IdentityHashMap<>());
        graph.values().forEach(value -> plan.chosenProducer(value).ifPresent(operation -> {
            inPlan.add(value);
            inPlan.add(operation);
        }));
        writer.dump(ctx, "plan", inPlan::contains);
    }
}
