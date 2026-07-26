package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.DirectiveInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The per-binding {@link Directive} the demand context carries into a strategy (design D9, open bag design D3 of
 * change {@code decouple-engine-from-strategy-semantics}): a strategy reads its {@code @Map}/{@code @MapEnum}
 * configuration from here rather than from a graph vertex. {@code source} is structural (feeds {@link
 * #sourcePath()}) and never forwarded as a generic input; {@code constant}/{@code defaultValue}/{@code
 * format}/{@code zone} — already decided present/absent by discovery — forward unchanged as {@link DirectiveInput}s.
 * Each {@code @MapEnum} entry becomes one repeated, structured {@code "enum"} input carrying its {@code source}/
 * {@code target} named parts, present regardless of whether a {@code @Map} directive is also bound at this path.
 */
@RequiredArgsConstructor
// each field backs the Directive accessor of the same name
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
final class BindingDirective implements Directive {

    private static final String SOURCE = "source";
    private static final String ENUM = "enum";

    private final List<String> sourcePath;
    private final List<DirectiveInput> inputs;

    /** Builds the {@link Directive} for a binding from its (possibly absent) {@code @Map} directive and {@code @MapEnum} table. */
    static BindingDirective from(
            final Optional<MappingDirective> directive, final List<EnumOverrideDirective> enumOverrides) {
        final var sourcePath = directive.map(d -> splitSource(d.getSource())).orElseGet(List::of);
        final var scalarInputs = directive
                .map(d -> d.getInputs().stream()
                        .filter(input -> !SOURCE.equals(input.getKey()))
                        .collect(toUnmodifiableList()))
                .orElseGet(List::of);
        final var enumInputs =
                enumOverrides.stream().map(BindingDirective::toEnumInput).collect(toUnmodifiableList());
        return new BindingDirective(
                sourcePath,
                Stream.concat(scalarInputs.stream(), enumInputs.stream()).collect(toUnmodifiableList()));
    }

    static DirectiveInput toEnumInput(final EnumOverrideDirective override) {
        return DirectiveInput.structured(
                ENUM,
                Map.of(SOURCE, override.getSource(), "target", override.getTarget()),
                override.getTargetSubject());
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
    public List<DirectiveInput> inputs() {
        return inputs;
    }
}
