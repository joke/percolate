package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class WeightsSpec extends Specification {

    def 'CONTAINER weight equals 2'() {
        expect:
        Weights.CONTAINER == 2
    }

    def 'CONTAINER weight is greater than STEP and less than EXPENSIVE'() {
        expect:
        Weights.STEP < Weights.CONTAINER
        Weights.CONTAINER < Weights.EXPENSIVE
    }

    def 'STEP and METHOD are the cheapest non-NOOP weights'() {
        expect:
        Weights.STEP == 1
        Weights.METHOD == 1
        Weights.NOOP == 0
    }

    def 'isSentinel recognises the unrealised marker'() {
        expect:
        Weights.isSentinel(Weights.SENTINEL_UNREALISED)
        !Weights.isSentinel(Weights.CONTAINER)
    }
}
