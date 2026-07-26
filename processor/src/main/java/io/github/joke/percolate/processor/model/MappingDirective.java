package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.Subject;
import io.github.joke.percolate.spi.Subjects;
import java.util.List;
import lombok.Value;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * One discovered {@code @Map} directive: {@code target} is always written (no default) and carries its own {@code
 * targetSubject}; every other member ({@code source}, {@code constant}, {@code defaultValue}, {@code format},
 * {@code zone}) is an open, keyed {@link DirectiveInput} present in {@link #inputs} only when actually written
 * (design D4 of change {@code decouple-engine-from-strategy-semantics}) — read via {@code AnnotationMirror
 * .getElementValues()}, so an empty string is present, not absent. Each
 * input's {@link Subject} was built once by discovery and is handed back unchanged — no code past this model reads
 * a raw {@code AnnotationMirror}/{@code AnnotationValue}.
 */
@Value
public class MappingDirective {

    private static final String SOURCE = "source";
    private static final String CONSTANT = "constant";
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final String FORMAT = "format";
    private static final String ZONE = "zone";

    String target;
    Subject targetSubject;
    List<DirectiveInput> inputs;

    /** Whether this directive declares a source path (moves a value from a parameter). */
    public boolean hasSource() {
        return has(SOURCE);
    }

    /** The declared source path, or {@code null} when absent. */
    public @Nullable String getSource() {
        return valueOf(SOURCE);
    }

    /** The source member's subject, or {@link Subjects#none()} when absent. */
    public Subject getSourceSubject() {
        return subjectOf(SOURCE);
    }

    /** Whether this directive declares a constant literal (supplies a value with no source). */
    public boolean hasConstant() {
        return has(CONSTANT);
    }

    /** The declared constant literal, or {@code null} when absent. */
    public @Nullable String getConstant() {
        return valueOf(CONSTANT);
    }

    /** The constant member's subject, or {@link Subjects#none()} when absent. */
    public Subject getConstantSubject() {
        return subjectOf(CONSTANT);
    }

    /** Whether this directive declares a default-value fallback. */
    public boolean hasDefaultValue() {
        return has(DEFAULT_VALUE);
    }

    /** The declared default value, or {@code null} when absent. */
    public @Nullable String getDefaultValue() {
        return valueOf(DEFAULT_VALUE);
    }

    /** The defaultValue member's subject, or {@link Subjects#none()} when absent. */
    public Subject getDefaultValueSubject() {
        return subjectOf(DEFAULT_VALUE);
    }

    /** Whether this directive declares a {@code format} option. */
    public boolean hasFormat() {
        return has(FORMAT);
    }

    /** The declared format pattern, or {@code null} when absent. */
    public @Nullable String getFormat() {
        return valueOf(FORMAT);
    }

    /** The format member's subject, or {@link Subjects#none()} when absent. */
    public Subject getFormatSubject() {
        return subjectOf(FORMAT);
    }

    /** Whether this directive declares a {@code zone} option. */
    public boolean hasZone() {
        return has(ZONE);
    }

    /** The declared zone id, or {@code null} when absent. */
    public @Nullable String getZone() {
        return valueOf(ZONE);
    }

    /** The zone member's subject, or {@link Subjects#none()} when absent. */
    public Subject getZoneSubject() {
        return subjectOf(ZONE);
    }

    @VisibleForTesting
    boolean has(final String key) {
        return input(key) != null;
    }

    @VisibleForTesting
    @Nullable
    String valueOf(final String key) {
        final var input = input(key);
        return input == null ? null : input.getValue().orElse(null);
    }

    @VisibleForTesting
    Subject subjectOf(final String key) {
        final var input = input(key);
        return input == null ? Subjects.none() : input.getSubject();
    }

    @VisibleForTesting
    @Nullable
    DirectiveInput input(final String key) {
        return inputs.stream().filter(i -> i.getKey().equals(key)).findFirst().orElse(null);
    }
}
