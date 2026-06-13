package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/** Dumps the transforms view (SAT vertices only), one {@code .transforms.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpTransformsStage implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        writer.dump(ctx, "transforms", vertex -> graph != null && graph.isSat(vertex));
    }
}
