package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PortSpec extends Specification {

    def 'the sourcing mode set is exactly the three closed modes, in declaration order'() {
        expect:
        Port.Sourcing.values().toList() == [Port.Sourcing.SUBTARGET, Port.Sourcing.REUSE, Port.Sourcing.REUSE_OR_MINT]
    }

    def 'a plain concrete port defaults to REUSE_OR_MINT'() {
        expect:
        new Port('value', TypeUniverse.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'a template port defaults to REUSE_OR_MINT'() {
        expect:
        new Port('value', TypeUniverse.STRING, Nullability.NON_NULL, (PortType) null).sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'Port.reuse builds a REUSE port'() {
        expect:
        Port.reuse('value', TypeUniverse.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.REUSE
    }

    def 'Port.subTarget builds a SUBTARGET port'() {
        expect:
        Port.subTarget('value', TypeUniverse.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.SUBTARGET
    }
}
