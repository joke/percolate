package io.github.joke.percolate.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

/**
 * Discovers {@code @Map} and {@code @MapList} directives on mapper methods via {@link javax.lang.model.element.AnnotationMirror} walking.
 * <p>
 * <b>CRITICAL:</b> Do NOT use {@link javax.lang.model.element.Element#getAnnotation(Class)} or
 * {@link javax.lang.model.element.Element#getAnnotationsByType(Class)} for {@code @Map} discovery.
 * These proxy methods discard {@link javax.lang.model.element.AnnotationMirror} and
 * {@link javax.lang.model.element.AnnotationValue} required for IDE-quality error positioning.
 * Use {@link com.google.auto.common.AnnotationMirrors} utilities instead (see design decision D6).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class DiscoverMappings implements Stage {

    private static final String MAP_FQN = Map.class.getCanonicalName();
    private static final String MAP_LIST_FQN = MapList.class.getCanonicalName();

    private final Elements elements;

    @Override
    public void run(MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        MapperMappings mappings = apply(shape);
        ctx.setMappings(mappings);
    }

    MapperMappings apply(final MapperShape shape) {
        final var methods =
                shape.getAbstractMethods().stream().map(this::toMethodMappings).collect(toUnmodifiableList());
        return new MapperMappings(shape.getType(), methods);
    }

    List<MappingDirective> extractDirectives(final List<? extends AnnotationMirror> mirrors) {
        return mirrors.stream().flatMap(this::directivesFromMirror).collect(toUnmodifiableList());
    }

    private Stream<MappingDirective> directivesFromMirror(final AnnotationMirror mirror) {
        final var annotationType = mirror.getAnnotationType().asElement();
        final var fqn = elements.getBinaryName((javax.lang.model.element.TypeElement) annotationType)
                .toString();
        if (MAP_FQN.equals(fqn)) {
            return Stream.of(toDirective(mirror));
        } else if (MAP_LIST_FQN.equals(fqn)) {
            return directivesFromMapList(mirror);
        }
        return Stream.empty();
    }

    private Stream<MappingDirective> directivesFromMapList(final AnnotationMirror mirror) {
        final var mapListValue = getAnnotationValue(mirror, "value");
        @SuppressWarnings("unchecked")
        final var annotationValues = (List<AnnotationValue>) mapListValue.getValue();
        return annotationValues.stream().map(av -> toDirective((AnnotationMirror) av.getValue()));
    }

    MappingDirective createDirective(
            final String target,
            final String source,
            final AnnotationMirror mirror,
            final AnnotationValue targetValue,
            final AnnotationValue sourceValue) {
        return new MappingDirective(target, source, mirror, targetValue, sourceValue);
    }

    private MethodMappings toMethodMappings(final ExecutableElement method) {
        return new MethodMappings(method, extractDirectives(method.getAnnotationMirrors()));
    }

    private MappingDirective toDirective(final AnnotationMirror mirror) {
        final var targetValue = getAnnotationValue(mirror, "target");
        final var sourceValue = getAnnotationValue(mirror, "source");
        return new MappingDirective(
                targetValue.getValue().toString(), sourceValue.getValue().toString(), mirror, targetValue, sourceValue);
    }
}
