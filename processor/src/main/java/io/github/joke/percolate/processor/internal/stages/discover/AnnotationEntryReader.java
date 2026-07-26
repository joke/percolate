package io.github.joke.percolate.processor.internal.stages.discover;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import jakarta.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The one thin {@code javax.lang.model} leaf shared by every {@code @Map}/{@code @MapEnum}-shaped directive reader
 * (design D4 of change {@code decouple-engine-from-strategy-semantics}): finds every mirror of {@code
 * annotationClass} among a method's annotations — classified by binary FQN, never {@code getAnnotation(Class)},
 * which discards the mirror an IDE-quality diagnostic needs — unwrapping its {@code @Repeatable} container
 * generically, and reads only the <b>written</b> members of each match via {@link AnnotationMirror#getElementValues()}
 * (never {@code AnnotationMirrors.getAnnotationValue}, which fills in defaults). It decides nothing about presence
 * beyond that: which members are mandatory, optional, or structured is the caller's concern.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class AnnotationEntryReader {

    private final Elements elements;

    /** Every mirror of {@code annotationClass} on {@code mirrors}, its own repeatable container unwrapped. */
    List<AnnotationMirror> entriesOf(
            final Class<? extends Annotation> annotationClass, final List<? extends AnnotationMirror> mirrors) {
        final var fqn = annotationClass.getCanonicalName();
        final var containerFqn = containerFqn(annotationClass);
        return mirrors.stream()
                .flatMap(mirror -> matching(mirror, fqn, containerFqn))
                .collect(toUnmodifiableList());
    }

    Stream<AnnotationMirror> matching(
            final AnnotationMirror mirror, final String fqn, final @Nullable String containerFqn) {
        final var mirrorFqn = annotationFqn(mirror);
        if (fqn.equals(mirrorFqn)) {
            return Stream.of(mirror);
        }
        if (containerFqn != null && containerFqn.equals(mirrorFqn)) {
            return unwrapContainer(mirror);
        }
        return Stream.empty();
    }

    /** {@code annotationClass}'s {@code @Repeatable} container FQN, or {@code null} when it declares none. */
    static @Nullable String containerFqn(final Class<? extends Annotation> annotationClass) {
        final var repeatable = annotationClass.getAnnotation(Repeatable.class);
        return repeatable == null ? null : repeatable.value().getCanonicalName();
    }

    @SuppressWarnings("unchecked")
    Stream<AnnotationMirror> unwrapContainer(final AnnotationMirror containerMirror) {
        final var containerValue = getAnnotationValue(containerMirror, "value");
        final var entries = (List<AnnotationValue>) containerValue.getValue();
        return entries.stream().map(av -> (AnnotationMirror) av.getValue());
    }

    String annotationFqn(final AnnotationMirror mirror) {
        final var annotationType = (TypeElement) mirror.getAnnotationType().asElement();
        return elements.getBinaryName(annotationType).toString();
    }

    /** {@code mirror}'s written members only, keyed by simple name — never a member left at its declared default. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded annotation processing; no concurrent access
    static Map<String, AnnotationValue> writtenMembers(final AnnotationMirror mirror) {
        final Map<String, AnnotationValue> written = new LinkedHashMap<>();
        mirror.getElementValues()
                .forEach((member, value) -> written.put(member.getSimpleName().toString(), value));
        return written;
    }
}
