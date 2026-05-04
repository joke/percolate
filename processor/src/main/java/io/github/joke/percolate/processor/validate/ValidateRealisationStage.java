package io.github.joke.percolate.processor.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateRealisationStage implements Stage {

    private final ValidateMarkersPhase markersPhase;
    private final ValidatePathsPhase pathsPhase;
    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        if (ctx.isScarred(diagnostics)) {
            return;
        }

        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        markersPhase.apply(graph, ctx.getMapperType());

        if (ctx.isScarred(diagnostics)) {
            return;
        }

        pathsPhase.apply(graph, ctx.getMapperType());
    }
}
