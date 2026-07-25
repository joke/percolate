package io.github.joke.percolate.processor.internal.stages.discover;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.MapEnum;
import io.github.joke.percolate.MapEnumList;
import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

/**
 * The thin {@code javax.lang.model} leaf of {@code @MapEnum} discovery, mirroring {@link AnnotationDirectiveReader}:
 * walks a method's {@link AnnotationMirror}s, keeps only the {@code @MapEnum}/{@code @MapEnumList} ones (classified
 * by binary FQN), unwraps a {@code @MapEnumList} container into its ordered elements, and projects each into an
 * {@link EnumOverrideDirective}. Unlike {@code @Map}, both {@code @MapEnum} members are mandatory (no default), so
 * there is no separate presence-decision builder — this reader hands the model type straight to its callers.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class EnumOverrideReader {

    private static final String MAP_ENUM_FQN = MapEnum.class.getCanonicalName();
    private static final String MAP_ENUM_LIST_FQN = MapEnumList.class.getCanonicalName();

    private final Elements elements;

    List<EnumOverrideDirective> extractOverrides(final List<? extends AnnotationMirror> mirrors) {
        return mirrors.stream().flatMap(this::overridesFromMirror).collect(toUnmodifiableList());
    }

    Stream<EnumOverrideDirective> overridesFromMirror(final AnnotationMirror mirror) {
        final var fqn = annotationFqn(mirror);
        if (MAP_ENUM_FQN.equals(fqn)) {
            return Stream.of(overrideFromMirror(mirror));
        } else if (MAP_ENUM_LIST_FQN.equals(fqn)) {
            return overridesFromMapEnumList(mirror);
        }
        return Stream.empty();
    }

    Stream<EnumOverrideDirective> overridesFromMapEnumList(final AnnotationMirror mirror) {
        final var listValue = getAnnotationValue(mirror, "value");
        @SuppressWarnings("unchecked")
        final var annotationValues = (List<AnnotationValue>) listValue.getValue();
        return annotationValues.stream().map(av -> overrideFromMirror((AnnotationMirror) av.getValue()));
    }

    EnumOverrideDirective overrideFromMirror(final AnnotationMirror mirror) {
        final var sourceValue = getAnnotationValue(mirror, "source");
        final var targetValue = getAnnotationValue(mirror, "target");
        return new EnumOverrideDirective(
                sourceValue.getValue().toString(), targetValue.getValue().toString(), mirror, sourceValue, targetValue);
    }

    String annotationFqn(final AnnotationMirror mirror) {
        final var annotationType = (TypeElement) mirror.getAnnotationType().asElement();
        return elements.getBinaryName(annotationType).toString();
    }
}
