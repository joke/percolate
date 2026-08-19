package io.github.joke.percolate.spi.builtins.assembly

import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.Weights.EXPENSIVE
import static io.github.joke.percolate.spi.Weights.STEP
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.BUILDER
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.CONSTRUCTOR
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.SETTER

/**
 * Pins the parse of the {@code percolate.construction.preference} processor option and the rank-to-weight mapping
 * built on it (design D4 of change {@code add-setter-assembly}). Every assembly strategy reads the option raw
 * through the generic {@code ResolveCtx.option(String)} seam and asks here for the weight of its own form. The
 * option has no typed field on {@code ProcessorOptions}: this is the one parser for it.
 */
@Tag('unit')
class ConstructionPreferenceSpec extends Specification {

    def 'an absent value ranks the constructor first, then the builder, then the setter'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.empty()) == [CONSTRUCTOR, BUILDER, SETTER]
    }

    def 'a named token ranks first and the remaining forms follow in declaration order'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of(raw)) == order

        where:
        raw                  | order
        'constructor'        | [CONSTRUCTOR, BUILDER, SETTER]
        'builder'            | [BUILDER, CONSTRUCTOR, SETTER]
        'setter'             | [SETTER, CONSTRUCTOR, BUILDER]
        'setter,builder'     | [SETTER, BUILDER, CONSTRUCTOR]
        'builder,setter'     | [BUILDER, SETTER, CONSTRUCTOR]
        'setter,builder,constructor' | [SETTER, BUILDER, CONSTRUCTOR]
    }

    def 'a token is read case-insensitively'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of(raw)) == order

        where:
        raw       | order
        'BUILDER' | [BUILDER, CONSTRUCTOR, SETTER]
        'Builder' | [BUILDER, CONSTRUCTOR, SETTER]
        'SeTTeR'  | [SETTER, CONSTRUCTOR, BUILDER]
    }

    def 'surrounding whitespace around a token is tolerated'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of(' setter , builder ')) == [SETTER, BUILDER, CONSTRUCTOR]
    }

    def 'an unrecognised token is ignored rather than failing the round'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of('sideways')) == [CONSTRUCTOR, BUILDER, SETTER]
        ConstructionPreference.effectiveOrder(Optional.of('setter,sideways')) == [SETTER, CONSTRUCTOR, BUILDER]
    }

    def 'a repeated token is ranked once, at its first position'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of('setter,setter,builder')) == [SETTER, BUILDER, CONSTRUCTOR]
    }

    def 'an empty value ranks every form in declaration order'() {
        expect:
        ConstructionPreference.effectiveOrder(Optional.of('')) == [CONSTRUCTOR, BUILDER, SETTER]
    }

    def 'rank 0 weighs STEP, rank 1 weighs EXPENSIVE, and each later rank weighs one more'() {
        expect:
        ConstructionPreference.weightOf(form, Optional.of('setter,builder,constructor')) == weight

        where:
        form        | weight
        SETTER      | STEP
        BUILDER     | EXPENSIVE
        CONSTRUCTOR | EXPENSIVE + 1
    }

    def 'the default ranking reproduces the weights this option carried before it grew a third form'() {
        expect:
        ConstructionPreference.weightOf(CONSTRUCTOR, Optional.empty()) == STEP
        ConstructionPreference.weightOf(BUILDER, Optional.empty()) == EXPENSIVE
        ConstructionPreference.weightOf(SETTER, Optional.empty()) == EXPENSIVE + 1
    }

    def 'an explicit builder preference reproduces the weights it carried before'() {
        expect:
        ConstructionPreference.weightOf(BUILDER, Optional.of('builder')) == STEP
        ConstructionPreference.weightOf(CONSTRUCTOR, Optional.of('builder')) == EXPENSIVE
        ConstructionPreference.weightOf(SETTER, Optional.of('builder')) == EXPENSIVE + 1
    }

    def 'an explicit constructor preference reproduces the weights it carried before'() {
        expect:
        ConstructionPreference.weightOf(CONSTRUCTOR, Optional.of('constructor')) == STEP
        ConstructionPreference.weightOf(BUILDER, Optional.of('constructor')) == EXPENSIVE
        ConstructionPreference.weightOf(SETTER, Optional.of('constructor')) == EXPENSIVE + 1
    }

    def 'no two forms ever share a weight, and no weight is negative'() {
        def weights = [CONSTRUCTOR, BUILDER, SETTER].collect { ConstructionPreference.weightOf(it, raw) }

        expect:
        weights.toUnique().size() == 3
        weights.every { it >= 0 }

        where:
        raw << [
                Optional.empty(),
                Optional.of('constructor'),
                Optional.of('builder'),
                Optional.of('setter'),
                Optional.of('setter,builder'),
                Optional.of('sideways'),
                Optional.of(''),
        ]
    }

    def 'the key is the full percolate option name'() {
        expect:
        ConstructionPreference.KEY == 'percolate.construction.preference'
    }
}
