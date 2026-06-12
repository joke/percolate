package io.github.joke.percolate.processor.stages.seed;

import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.spi.Directive;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * The real per-binding {@link Directive} the seed stage stamps onto a frontier node so a strategy reads its
 * {@code @Map} configuration from local context. Presence of {@link #constant()} / {@link #defaultValue()} was
 * already decided against the {@code Map.UNSET} sentinel by discovery (absent members are {@code null} on the
 * {@link MappingDirective}); an empty string is therefore reported present, never absent.
 */
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName") // each field backs the Directive accessor of the same name
final class MapDirective implements Directive {

    private final List<String> sourcePath;
    private final Optional<String> constant;
    private final Optional<String> defaultValue;

    static MapDirective from(final MappingDirective directive) {
        return new MapDirective(
                splitSource(directive.getSource()),
                Optional.ofNullable(directive.getConstant()),
                Optional.ofNullable(directive.getDefaultValue()));
    }

    private static List<String> splitSource(final @org.jspecify.annotations.Nullable String source) {
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
}
