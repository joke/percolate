package io.github.joke.percolate.spi.builtins;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

// The one thin javax.lang.model helper shared by every built-in io.github.joke.percolate.spi.DirectiveReader
// (design D4/D7 of change decouple-engine-from-strategy-semantics): finds every mirror of a mapping annotation
// among a method's own annotations — classified by qualified name (every built-in mapping annotation is a top-
// level type, so this needs no Elements.getBinaryName compiler service) — unwrapping its @Repeatable container
// generically, and reads only the written members of each match via AnnotationMirror.getElementValues() (never
// AnnotationMirrors.getAnnotationValue, which fills in defaults). It decides nothing about presence beyond
// that: which members are mandatory, optional, or structured is the caller's concern.
@UtilityClass
@CoverageIgnore
public class AnnotationEntries {

    // Every mirror of annotationClass on method, its own repeatable container unwrapped.
    public static List<AnnotationMirror> entriesOf(
            final Class<? extends Annotation> annotationClass, final ExecutableElement method) {
        final var fqn = annotationClass.getCanonicalName();
        final var containerFqn = containerFqn(annotationClass);
        return method.getAnnotationMirrors().stream()
                .flatMap(mirror -> matching(mirror, fqn, containerFqn))
                .collect(toUnmodifiableList());
    }

    @VisibleForTesting
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

    // annotationClass's @Repeatable container FQN, or null when it declares none.
    @VisibleForTesting
    static @Nullable String containerFqn(final Class<? extends Annotation> annotationClass) {
        final var repeatable = annotationClass.getAnnotation(Repeatable.class);
        return repeatable == null ? null : repeatable.value().getCanonicalName();
    }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    static Stream<AnnotationMirror> unwrapContainer(final AnnotationMirror containerMirror) {
        final var containerValue =
                requireNonNull(writtenMembers(containerMirror).get("value"));
        final var entries = (List<AnnotationValue>) containerValue.getValue();
        return entries.stream().map(av -> (AnnotationMirror) av.getValue());
    }

    @VisibleForTesting
    static String annotationFqn(final AnnotationMirror mirror) {
        final var element = mirror.getAnnotationType().asElement();
        return ((TypeElement) element).getQualifiedName().toString();
    }

    // mirror's written members only, keyed by simple name — never a member left at its declared default.
    public static Map<String, AnnotationValue> writtenMembers(final AnnotationMirror mirror) {
        final var written = new LinkedHashMap<String, AnnotationValue>();
        mirror.getElementValues()
                .forEach((member, value) -> written.put(member.getSimpleName().toString(), value));
        return written;
    }
}
