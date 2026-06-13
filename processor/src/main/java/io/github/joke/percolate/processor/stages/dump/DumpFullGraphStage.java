package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/** Dumps the post-expansion full graph, one {@code .full.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpFullGraphStage implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        writer.dump(ctx, "full", vertex -> true);
    }
}
