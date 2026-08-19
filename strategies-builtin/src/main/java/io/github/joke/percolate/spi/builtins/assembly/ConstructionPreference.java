package io.github.joke.percolate.spi.builtins.assembly;

import java.util.List;
import java.util.Optional;

import static io.github.joke.percolate.spi.Weights.EXPENSIVE;
import static io.github.joke.percolate.spi.Weights.STEP;
import static java.util.Arrays.stream;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.concat;

/**
 * Which assembly form the author prefers when a target admits more than one — the parsed
 * {@code -Apercolate.construction.preference} processor option, read raw through
 * {@link io.github.joke.percolate.spi.ResolveCtx#option(String)} by each assembly strategy.
 *
 * <p>The option's value is an <b>ordered, comma-separated list</b> of the tokens below. Every token the author
 * omitted is appended in declaration order, so the effective order always ranks all three forms and no two forms
 * ever share a weight (design D4 of change {@code add-setter-assembly}). A binary flag could not do that: with a
 * third form, two of them would both land on {@code EXPENSIVE} and the minimum-cost fold would resolve the tie by
 * an accident no author declared.
 *
 * <p>It lives here, with the strategies that give it meaning, rather than in {@code percolate-spi}: the SPI gains
 * no assembly-named type, and there is exactly one parser for the option. Each assembly strategy asks for the
 * weight of <b>its own</b> form and never inspects another strategy or names another form's token, so strategy
 * myopia holds. Because the plan fold is minimum-cost, a lower rank takes the lower weight.
 */
public enum ConstructionPreference {

    /** A constructor call. Ranks first when the option is absent. */
    CONSTRUCTOR,

    /** A builder chain. */
    BUILDER,

    /** A no-argument constructor followed by JavaBean setters. */
    SETTER;

    /** The processor-option key each assembly strategy reads through the generic seam. */
    public static final String KEY = "percolate.construction.preference";

    private static final String SEPARATOR = ",";

    /**
     * The weight {@code form} carries under the preference {@code raw} names. Rank 0 weighs
     * {@link io.github.joke.percolate.spi.Weights#STEP}, rank 1 weighs
     * {@link io.github.joke.percolate.spi.Weights#EXPENSIVE}, and every later rank weighs one more than the last
     * — three distinct, non-negative weights, reproducing the two weights this option produced before it grew a
     * third form.
     */
    public static int weightOf(final ConstructionPreference form, final Optional<String> raw) {
        final var rank = effectiveOrder(raw).indexOf(form);
        return rank == 0 ? STEP : EXPENSIVE + rank - 1;
    }

    /**
     * The full ranking {@code raw} implies: the tokens it names, in its own order, then every remaining form in
     * declaration order. An unrecognised or repeated token contributes nothing and never fails the round.
     */
    public static List<ConstructionPreference> effectiveOrder(final Optional<String> raw) {
        final var named = namedForms(raw);
        return concat(named.stream(), stream(values()).filter(form -> !named.contains(form)))
                .collect(toUnmodifiableList());
    }

    // The recognised tokens raw names, in its own order, without repeats.
    private static List<ConstructionPreference> namedForms(final Optional<String> raw) {
        return raw.map(value -> stream(value.split(SEPARATOR))
                        .map(ConstructionPreference::parse)
                        .flatMap(Optional::stream)
                        .distinct()
                        .collect(toUnmodifiableList()))
                .orElseGet(List::of);
    }

    // One token, trimmed and case-insensitive; an unrecognised one is dropped rather than failing the round.
    private static Optional<ConstructionPreference> parse(final String token) {
        final var wanted = token.trim().toUpperCase(ROOT);
        return stream(values()).filter(form -> form.name().equals(wanted)).findFirst();
    }
}
