package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MapperShape;
import jakarta.inject.Inject;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;

/**
 * Reduces a mapper {@code TypeElement} to a {@link MapperShape} of its abstract, non-{@code Object} methods — the
 * methods the mapper must implement. The genuinely compiler-backed member enumeration lives in the thin
 * {@link AbstractMethodReader}; the pure keep/drop decision lives in {@link AbstractMethodFilter}. This stage is thin
 * glue between them.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverAbstractMethodsStage implements Stage {

    private final AbstractMethodReader reader;
    private final AbstractMethodFilter filter;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = apply(ctx.getMapperType());
        ctx.setShape(shape);
    }

    MapperShape apply(final TypeElement typeElement) {
        return new MapperShape(typeElement, filter.abstractMethods(reader.readMethods(typeElement)));
    }
}
