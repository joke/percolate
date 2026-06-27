package io.github.joke.percolate.processor.internal.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/** Dumps the transforms view (reachable vertices only), one {@code .transforms.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpTransformsStage implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            writer.dump(ctx, "transforms", vertex -> false);
            return;
        }
        final var plan = ExtractedPlan.extract(graph);
        writer.dump(ctx, "transforms", plan::reachable);
    }
}
