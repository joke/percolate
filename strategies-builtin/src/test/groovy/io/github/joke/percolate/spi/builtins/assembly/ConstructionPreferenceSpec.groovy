package io.github.joke.percolate.spi.builtins.assembly

import spock.lang.Specification
import spock.lang.Tag

/**
 * Pins the parse of the {@code percolate.construction.preference} processor option, which every assembly strategy
 * reads raw through the generic {@code ResolveCtx.option(String)} seam and interprets for itself. The option has no
 * typed field on {@code ProcessorOptions}: this is the one parser for it.
 */
@Tag('unit')
class ConstructionPreferenceSpec extends Specification {

    def 'an absent value means CONSTRUCTOR'() {
        expect:
        ConstructionPreference.from(Optional.empty()) == ConstructionPreference.CONSTRUCTOR
    }

    def 'a recognised value is read case-insensitively'() {
        expect:
        ConstructionPreference.from(Optional.of(raw)) == parsed

        where:
        raw           | parsed
        'builder'     | ConstructionPreference.BUILDER
        'BUILDER'     | ConstructionPreference.BUILDER
        'Builder'     | ConstructionPreference.BUILDER
        'constructor' | ConstructionPreference.CONSTRUCTOR
        'CONSTRUCTOR' | ConstructionPreference.CONSTRUCTOR
    }

    def 'an unrecognised value degrades to CONSTRUCTOR rather than failing the round'() {
        expect:
        ConstructionPreference.from(Optional.of('sideways')) == ConstructionPreference.CONSTRUCTOR
    }

    def 'the key is the full percolate option name'() {
        expect:
        ConstructionPreference.KEY == 'percolate.construction.preference'
    }
}
