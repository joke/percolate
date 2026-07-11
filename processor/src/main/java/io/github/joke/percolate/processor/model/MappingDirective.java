package io.github.joke.percolate.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One discovered {@code @Map} directive. {@link #source}, {@link #constant}, {@link #defaultValue}, {@link #format},
 * and {@link #zone} are each {@code null} when the member is <em>absent</em> (left at the {@code Map.UNSET}
 * sentinel) and non-null — including the empty string — when the member is <em>present</em>. The matching
 * {@code *Value} {@link AnnotationValue} is populated only when its member is present, so a diagnostic can underline
 * the exact written literal; an absent member carries a {@code null} value (there is no written token to point at).
 */
@Value
public class MappingDirective {
    String target;

    @Nullable
    String source;

    @Nullable
    String constant;

    @Nullable
    String defaultValue;

    @Nullable
    String format;

    @Nullable
    String zone;

    AnnotationMirror mirror;
    AnnotationValue targetValue;

    @Nullable
    AnnotationValue sourceValue;

    @Nullable
    AnnotationValue constantValue;

    @Nullable
    AnnotationValue defaultValueValue;

    @Nullable
    AnnotationValue formatValue;

    @Nullable
    AnnotationValue zoneValue;

    /** Whether this directive declares a source path (moves a value from a parameter). */
    public boolean hasSource() {
        return source != null;
    }

    /** Whether this directive declares a constant literal (supplies a value with no source). */
    public boolean hasConstant() {
        return constant != null;
    }

    /** Whether this directive declares a default-value fallback. */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    /** Whether this directive declares a {@code format} option. */
    public boolean hasFormat() {
        return format != null;
    }

    /** Whether this directive declares a {@code zone} option. */
    public boolean hasZone() {
        return zone != null;
    }
}
