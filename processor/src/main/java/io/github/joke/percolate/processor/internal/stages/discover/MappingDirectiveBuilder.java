package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.processor.model.MappingDirective;
import jakarta.inject.Inject;
import javax.lang.model.element.AnnotationValue;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The pure decision half of mapping discovery: it turns a plain {@link RawDirective} into a {@link MappingDirective},
 * deciding each member's <em>presence</em> against the {@code Map.UNSET} sentinel — never {@link String#isEmpty()}, so
 * an empty string is a present value — and forwarding the opaque {@link AnnotationValue}/mirror tokens untouched (a
 * present member keeps its token so a diagnostic can underline the written literal; an absent member carries a
 * {@code null} token, there being no written literal to point at). It interrogates no {@code javax.lang.model} value,
 * so it unit-tests on plain data with the tokens as never-stubbed opaque {@code Mock()}s.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
final class MappingDirectiveBuilder {

    MappingDirective toDirective(final RawDirective raw) {
        final var source = present(raw.getSource());
        final var constant = present(raw.getConstant());
        final var defaultValue = present(raw.getDefaultValue());
        final var format = present(raw.getFormat());
        final var zone = present(raw.getZone());
        return new MappingDirective(
                raw.getTarget(),
                source,
                constant,
                defaultValue,
                format,
                zone,
                raw.getMirror(),
                raw.getTargetValue(),
                valueIfPresent(source, raw.getSourceValue()),
                valueIfPresent(constant, raw.getConstantValue()),
                valueIfPresent(defaultValue, raw.getDefaultValueValue()),
                valueIfPresent(format, raw.getFormatValue()),
                valueIfPresent(zone, raw.getZoneValue()));
    }

    /**
     * The member's written string, or {@code null} when it is absent (left at {@link Map#UNSET}). Presence is decided
     * against the sentinel, never via {@link String#isEmpty()}: an empty string is a present value.
     */
    @Nullable
    String present(final String raw) {
        return Map.UNSET.equals(raw) ? null : raw;
    }

    @Nullable
    AnnotationValue valueIfPresent(final @Nullable String presentString, final AnnotationValue value) {
        return presentString == null ? null : value;
    }
}
