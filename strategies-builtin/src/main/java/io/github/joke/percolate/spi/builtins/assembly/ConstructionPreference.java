package io.github.joke.percolate.spi.builtins.assembly;

import java.util.Optional;

import static java.util.Locale.ROOT;

/**
 * Which assembly form the author prefers when a target admits more than one — the parsed
 * {@code -Apercolate.construction.preference} processor option, read raw through
 * {@link io.github.joke.percolate.spi.ResolveCtx#option(String)} by each assembly strategy.
 *
 * <p>It lives here, with the strategies that give it meaning, rather than in {@code percolate-spi}: the SPI gains no
 * builder-named type, and there is exactly one parser for the option. Each assembly strategy prices <b>itself</b>
 * from this value and never inspects another strategy, so strategy myopia holds. Because the plan fold is
 * minimum-cost, the preferred form takes the lower weight.
 */
public enum ConstructionPreference {

    /** Prefer a constructor call. The default when the option is absent or unrecognised. */
    CONSTRUCTOR,

    /** Prefer a builder chain. */
    BUILDER;

    /** The processor-option key each assembly strategy reads through the generic seam. */
    public static final String KEY = "percolate.construction.preference";

    /**
     * The preference {@code raw} names, case-insensitively; an absent or unrecognised value means
     * {@link #CONSTRUCTOR}. Absence is folded into the parse by defaulting the <b>string</b> rather than the
     * result, so the parse outcome reaches the caller directly instead of through an {@code Optional.map} that
     * would silently absorb it.
     */
    public static ConstructionPreference from(final Optional<String> raw) {
        return parse(raw.orElse(CONSTRUCTOR.name()));
    }

    // An unrecognised construction.preference degrades to CONSTRUCTOR — never fails the round.
    private static ConstructionPreference parse(final String raw) {
        try {
            return valueOf(raw.toUpperCase(ROOT));
        } catch (final IllegalArgumentException e) {
            return CONSTRUCTOR;
        }
    }
}
