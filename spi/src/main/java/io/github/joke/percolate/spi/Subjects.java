package io.github.joke.percolate.spi;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The sole constructor and resolver of {@link Subject} (design D14 of change
 * {@code decouple-engine-from-strategy-semantics}): a reader calls {@link #of} while parsing an annotation member to
 * capture where a future diagnostic about that member should point; the framework's own emitter is the sole caller
 * of {@link #resolve}. Neither a strategy nor a reader ever inspects what a {@link Subject} holds.
 */
public final class Subjects {

    private Subjects() {}

    /** A subject anchored on an annotation member: {@code mirror}/{@code value} may be {@code null} (element-only). */
    public static Subject of(
            final Element element, final @Nullable AnnotationMirror mirror, final @Nullable AnnotationValue value) {
        return new AnnotationSubject(element, mirror, value);
    }

    /** The subject used when a diagnostic has no more specific owning token — resolves to the mapper type. */
    public static Subject none() {
        return NoneSubject.INSTANCE;
    }

    /** Resolves {@code subject} to the element/mirror/value the {@code Messager} positions on, falling back to {@code mapperType} for {@link #none()}. */
    public static Position resolve(final Subject subject, final Element mapperType) {
        if (subject instanceof AnnotationSubject) {
            final var annotationSubject = (AnnotationSubject) subject;
            return new Position(annotationSubject.element, annotationSubject.mirror, annotationSubject.value);
        }
        return new Position(mapperType, null, null);
    }

    /** The resolved element/mirror/value triple a {@link Subject} positions a diagnostic at. */
    @Value
    public static class Position {
        Element element;

        @Nullable
        AnnotationMirror mirror;

        @Nullable
        AnnotationValue value;
    }

    private static final class AnnotationSubject implements Subject {
        private final Element element;
        private final @Nullable AnnotationMirror mirror;
        private final @Nullable AnnotationValue value;

        AnnotationSubject(
                final Element element, final @Nullable AnnotationMirror mirror, final @Nullable AnnotationValue value) {
            this.element = element;
            this.mirror = mirror;
            this.value = value;
        }
    }

    private enum NoneSubject implements Subject {
        INSTANCE
    }
}
