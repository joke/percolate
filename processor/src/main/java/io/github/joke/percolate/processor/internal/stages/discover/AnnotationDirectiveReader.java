package io.github.joke.percolate.processor.internal.stages.discover;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

/**
 * The thin {@code javax.lang.model} leaf of mapping discovery: it walks a method's {@link AnnotationMirror}s, keeps
 * only the {@code @Map}/{@code @MapList} ones (classified by binary FQN — never {@code getAnnotation(Class)}, which
 * discards the mirrors needed for IDE-quality error positioning, design D6), unwraps a {@code @MapList} container into
 * its ordered elements, and projects each into a plain {@link RawDirective}. It decides <em>nothing</em>: the
 * {@code Map.UNSET} presence test and {@code MappingDirective} assembly live in {@link MappingDirectiveBuilder}. This
 * reader is exercised end-to-end by the compile-based feature-e2e layer (real {@code CompileResolveCtx}), not by a
 * unit-test javac substrate.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class AnnotationDirectiveReader {

    private static final String MAP_FQN = Map.class.getCanonicalName();
    private static final String MAP_LIST_FQN = MapList.class.getCanonicalName();

    private final Elements elements;

    List<RawDirective> extractRawDirectives(final List<? extends AnnotationMirror> mirrors) {
        return mirrors.stream().flatMap(this::rawDirectivesFromMirror).collect(toUnmodifiableList());
    }

    Stream<RawDirective> rawDirectivesFromMirror(final AnnotationMirror mirror) {
        final var fqn = annotationFqn(mirror);
        if (MAP_FQN.equals(fqn)) {
            return Stream.of(rawFromMirror(mirror));
        } else if (MAP_LIST_FQN.equals(fqn)) {
            return rawDirectivesFromMapList(mirror);
        }
        return Stream.empty();
    }

    Stream<RawDirective> rawDirectivesFromMapList(final AnnotationMirror mirror) {
        final var mapListValue = getAnnotationValue(mirror, "value");
        @SuppressWarnings("unchecked")
        final var annotationValues = (List<AnnotationValue>) mapListValue.getValue();
        return annotationValues.stream().map(av -> rawFromMirror((AnnotationMirror) av.getValue()));
    }

    RawDirective rawFromMirror(final AnnotationMirror mirror) {
        final var targetValue = getAnnotationValue(mirror, "target");
        final var sourceValue = getAnnotationValue(mirror, "source");
        final var constantValue = getAnnotationValue(mirror, "constant");
        final var defaultValueValue = getAnnotationValue(mirror, "defaultValue");
        final var formatValue = getAnnotationValue(mirror, "format");
        final var zoneValue = getAnnotationValue(mirror, "zone");
        return new RawDirective(
                targetValue.getValue().toString(),
                sourceValue.getValue().toString(),
                constantValue.getValue().toString(),
                defaultValueValue.getValue().toString(),
                formatValue.getValue().toString(),
                zoneValue.getValue().toString(),
                mirror,
                targetValue,
                sourceValue,
                constantValue,
                defaultValueValue,
                formatValue,
                zoneValue);
    }

    String annotationFqn(final AnnotationMirror mirror) {
        final var annotationType = (TypeElement) mirror.getAnnotationType().asElement();
        return elements.getBinaryName(annotationType).toString();
    }
}
