package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

// Indexes a mapper type's single-parameter, non-Object methods so the engine can later ask which of them
// produce a demanded output type. The genuinely compiler-backed member enumeration lives in the thin
// CallableMethodIndexer; the pure filter (and the assignability-answering IndexCallableMethods view it builds)
// lives in CallableMethodFilter. This stage is thin glue between them.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverCallableMethodsStage implements Stage {

    private final CallableMethodIndexer indexer;
    private final CallableMethodFilter filter;

    @Override
    public void run(final MapperContext ctx) {
        final var mapperType = ctx.getMapperType();
        ctx.setCallableMethods(filter.filter(indexer.index(mapperType)));
    }
}
