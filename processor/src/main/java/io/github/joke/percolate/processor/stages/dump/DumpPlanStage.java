package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/** Dumps the chosen-plan view, one {@code .plan.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpPlan implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        writer.dump(ctx, "plan", MapperGraph::planView);
    }
}
