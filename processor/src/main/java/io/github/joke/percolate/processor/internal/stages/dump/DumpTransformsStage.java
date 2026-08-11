package io.github.joke.percolate.processor.internal.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import static io.github.joke.percolate.processor.internal.graph.ExtractedPlan.extract;

// Dumps the transforms view (reachable vertices only), one .transforms.dot file per scope.
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
        final var plan = extract(graph);
        writer.dump(ctx, "transforms", plan::reachable);
    }
}
