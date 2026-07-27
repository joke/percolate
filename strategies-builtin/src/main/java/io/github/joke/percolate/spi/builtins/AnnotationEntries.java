package io.github.joke.percolate.spi.builtins;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The one thin {@code javax.lang.model} helper shared by every built-in {@link io.github.joke.percolate.spi.DirectiveReader}
 * (design D4/D7 of change {@code decouple-engine-from-strategy-semantics}): finds every mirror of a mapping
 * annotation among a method's own annotations — classified by qualified name (every built-in mapping annotation is a
 * top-level type, so this needs no {@code Elements.getBinaryName} compiler service) — unwrapping its
 * {@code @Repeatable} container generically, and reads only the <b>written</b> members of each match via
 * {@link AnnotationMirror#getElementValues()} (never {@code AnnotationMirrors.getAnnotationValue}, which fills in
 * defaults). It decides nothing about presence beyond that: which members are mandatory, optional, or structured is
 * the caller's concern.
 */
@UtilityClass
@CoverageIgnore
public class AnnotationEntries {

    /** Every mirror of {@code annotationClass} on {@code method}, its own repeatable container unwrapped. */
    public static List<AnnotationMirror> entriesOf(
            final Class<? extends Annotation> annotationClass, final ExecutableElement method) {
        final var fqn = annotationClass.getCanonicalName();
        final var containerFqn = containerFqn(annotationClass);
        return method.getAnnotationMirrors().stream()
                .flatMap(mirror -> matching(mirror, fqn, containerFqn))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    static Stream<AnnotationMirror> matching(
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
    static Stream<AnnotationMirror> unwrapContainer(final AnnotationMirror containerMirror) {
        final var containerValue =
                Objects.requireNonNull(writtenMembers(containerMirror).get("value"));
        final var entries = (List<AnnotationValue>) containerValue.getValue();
        return entries.stream().map(av -> (AnnotationMirror) av.getValue());
    }

    static String annotationFqn(final AnnotationMirror mirror) {
        final var element = mirror.getAnnotationType().asElement();
        return ((TypeElement) element).getQualifiedName().toString();
    }

    /** {@code mirror}'s written members only, keyed by simple name — never a member left at its declared default. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded annotation processing; no concurrent access
    public static Map<String, AnnotationValue> writtenMembers(final AnnotationMirror mirror) {
        final Map<String, AnnotationValue> written = new LinkedHashMap<>();
        mirror.getElementValues()
                .forEach((member, value) -> written.put(member.getSimpleName().toString(), value));
        return written;
    }
}
