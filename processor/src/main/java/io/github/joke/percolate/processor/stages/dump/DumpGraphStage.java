package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/** Dumps the pre-expansion seed graph, one {@code .seed.dot} file per scope. */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpGraph implements Stage {

    private final GraphDumpWriter writer;

    @Override
    public void run(final MapperContext ctx) {
        writer.dump(ctx, "seed", graph -> graph);
    }
}
