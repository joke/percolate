package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.EnumOverride;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The per-binding {@link Directive} the demand context carries into a strategy (design D9): a strategy reads its
 * {@code @Map}/{@code @MapEnum} configuration from here rather than from a graph vertex. Presence of
 * {@link #constant()} / {@link #defaultValue()} was already decided against the {@code Map.UNSET} sentinel by
 * discovery (absent members are {@code null} on the {@link MappingDirective}); an empty string is reported present,
 * never absent. {@link #enumOverrides()} carries the method's {@code @MapEnum} declarations regardless of whether a
 * {@code @Map} directive is also bound at this path — a plain conversion method typically carries only the former.
 */
@RequiredArgsConstructor
// each field backs the Directive accessor of the same name
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
final class BindingDirective implements Directive {

    private final List<String> sourcePath;
    private final Optional<String> constant;
    private final Optional<String> defaultValue;
    private final Optional<String> format;
    private final Optional<String> zone;
    private final List<EnumOverride> enumOverrides;

    /** Builds the {@link Directive} for a binding from its (possibly absent) {@code @Map} directive and {@code @MapEnum} table. */
    static BindingDirective from(
            final Optional<MappingDirective> directive, final List<EnumOverrideDirective> enumOverrides) {
        final var overrides = toSpiOverrides(enumOverrides);
        return directive
                .map(d -> new BindingDirective(
                        splitSource(d.getSource()),
                        Optional.ofNullable(d.getConstant()),
                        Optional.ofNullable(d.getDefaultValue()),
                        Optional.ofNullable(d.getFormat()),
                        Optional.ofNullable(d.getZone()),
                        overrides))
                .orElseGet(() -> new BindingDirective(
                        List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), overrides));
    }

    static List<String> splitSource(final @Nullable String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.of(source.split("\\.", -1));
    }

    static List<EnumOverride> toSpiOverrides(final List<EnumOverrideDirective> enumOverrides) {
        return enumOverrides.stream()
                .map(raw -> new EnumOverride(raw.getSource(), raw.getTarget()))
                .collect(toUnmodifiableList());
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

    @Override
    public List<EnumOverride> enumOverrides() {
        return enumOverrides;
    }
}
