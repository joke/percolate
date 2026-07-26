package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.model.MethodMappings;
import jakarta.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Discovers {@code @Map}/{@code @MapList} and {@code @MapEnum}/{@code @MapEnumList} directives on mapper methods.
 * The genuinely compiler-backed {@code javax.lang.model} walk lives in {@link MapDirectiveReader} and
 * {@link MapEnumDirectiveReader} (design D4 of change {@code decouple-engine-from-strategy-semantics}), both built
 * on the shared {@link AnnotationEntryReader}. This stage is thin glue: it threads each method through the two
 * readers and installs the resulting {@link MapperMappings} and per-method-scope {@link GoalSpec}s (which
 * additionally carry the method's {@code @MapEnum} declarations) on the context.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverMappingsStage implements Stage {

    private final MapDirectiveReader mapReader;
    private final MapEnumDirectiveReader mapEnumReader;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        final var mappings = apply(shape);
        ctx.setMappings(mappings);
        mappings.getMethods().forEach(method -> ctx.getGoalSpecs()
                .put(
                        new MethodScope(method.getMethod()),
                        GoalSpec.from(method.getDirectives(), mapEnumReader.extractOverrides(method.getMethod()))));
    }

    MapperMappings apply(final MapperShape shape) {
        final var methods =
                shape.getAbstractMethods().stream().map(this::toMethodMappings).collect(toUnmodifiableList());
        return new MapperMappings(shape.getType(), methods);
    }

    MethodMappings toMethodMappings(final ExecutableElement method) {
        return new MethodMappings(method, mapReader.extractDirectives(method));
    }
}
