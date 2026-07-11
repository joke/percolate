package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.spi.Directive;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The per-binding {@link Directive} the demand context carries into a strategy (design D9): a strategy reads its
 * {@code @Map} configuration from here rather than from a graph vertex. Presence of {@link #constant()} /
 * {@link #defaultValue()} was already decided against the {@code Map.UNSET} sentinel by discovery (absent members
 * are {@code null} on the {@link MappingDirective}); an empty string is reported present, never absent.
 */
@RequiredArgsConstructor
// each field backs the Directive accessor of the same name; a pure adapter is expected to look like a data class
@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName", "PMD.DataClass"})
final class BindingDirective implements Directive {

    private final List<String> sourcePath;
    private final Optional<String> constant;
    private final Optional<String> defaultValue;
    private final Optional<String> format;
    private final Optional<String> zone;

    static BindingDirective from(final MappingDirective directive) {
        return new BindingDirective(
                splitSource(directive.getSource()),
                Optional.ofNullable(directive.getConstant()),
                Optional.ofNullable(directive.getDefaultValue()),
                Optional.ofNullable(directive.getFormat()),
                Optional.ofNullable(directive.getZone()));
    }

    static List<String> splitSource(final @Nullable String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.of(source.split("\\.", -1));
    }

    @Override
    public List<String> sourcePath() {
        return sourcePath;
    }

    @Override
    public Optional<String> constant() {
        return constant;
    }

    @Override
    public Optional<String> defaultValue() {
        return defaultValue;
    }

    @Override
    public Optional<String> format() {
        return format;
    }

    @Override
    public Optional<String> zone() {
        return zone;
    }
}
