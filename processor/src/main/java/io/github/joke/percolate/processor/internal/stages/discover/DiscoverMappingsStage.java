package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Discovers {@code @Map} and {@code @MapList} directives on mapper methods. The genuinely compiler-backed
 * {@link javax.lang.model.element.AnnotationMirror} walk lives in the thin {@link AnnotationDirectiveReader}; the pure
 * {@code Map.UNSET}-presence decision and {@link MappingDirective} assembly live in {@link MappingDirectiveBuilder}.
 * This stage is thin glue: it threads a method's mirrors through the reader, maps each {@link RawDirective} through the
 * builder, and installs the resulting {@link MapperMappings} and per-method-scope {@link GoalSpec}s on the context.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverMappingsStage implements Stage {

    private final AnnotationDirectiveReader reader;
    private final MappingDirectiveBuilder builder;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        final var mappings = apply(shape);
        ctx.setMappings(mappings);
        mappings.getMethods().forEach(method -> ctx.getGoalSpecs()
                .put(new MethodScope(method.getMethod()), GoalSpec.from(method.getDirectives())));
    }

    MapperMappings apply(final MapperShape shape) {
        final var methods =
                shape.getAbstractMethods().stream().map(this::toMethodMappings).collect(toUnmodifiableList());
        return new MapperMappings(shape.getType(), methods);
    }

    List<MappingDirective> extractDirectives(final List<? extends AnnotationMirror> mirrors) {
        return reader.extractRawDirectives(mirrors).stream()
                .map(builder::toDirective)
                .collect(toUnmodifiableList());
    }

    MethodMappings toMethodMappings(final ExecutableElement method) {
        return new MethodMappings(method, extractDirectives(method.getAnnotationMirrors()));
    }
}
