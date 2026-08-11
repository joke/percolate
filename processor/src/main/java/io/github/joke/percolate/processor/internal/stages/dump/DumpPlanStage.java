package io.github.joke.percolate.processor.internal.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import java.util.IdentityHashMap;
import lombok.RequiredArgsConstructor;

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
        final var inPlan = newSetFromMap(new IdentityHashMap<>());
        graph.values().forEach(value -> plan.chosenProducer(value).ifPresent(operation -> {
            inPlan.add(value);
            inPlan.add(operation);
        }));
        writer.dumpWithRefusals(ctx, "plan", inPlan::contains, false);
    }
}
