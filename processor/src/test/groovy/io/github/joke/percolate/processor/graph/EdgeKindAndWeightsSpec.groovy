package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class EdgeKindAndWeightsSpec extends Specification {

    def 'EdgeKind has exactly four values in declaration order'() {
        expect:
        EdgeKind.values().length == 4
        EdgeKind.values()[0] == EdgeKind.SEED
        EdgeKind.values()[1] == EdgeKind.REALISED
        EdgeKind.values()[2] == EdgeKind.MARKER
        EdgeKind.values()[3] == EdgeKind.SUB_SEED
    }

    def 'EdgeKind.values() returns stable order'() {
        expect:
        EdgeKind.values()[0].name() == 'SEED'
        EdgeKind.values()[1].name() == 'REALISED'
        EdgeKind.values()[2].name() == 'MARKER'
        EdgeKind.values()[3].name() == 'SUB_SEED'
    }

    def 'Weights constants have correct values'() {
        expect:
        Weights.NOOP == 0 as int
        Weights.STEP == 1 as int
        Weights.COPY == 2 as int
        Weights.EXPENSIVE == 3 as int
        Weights.SENTINEL_UNREALISED == (Integer.MAX_VALUE / 2) as int
    }

    def 'Weights.SENTINEL_UNREALISED is Integer.MAX_VALUE / 2'() {
        expect:
        Weights.SENTINEL_UNREALISED == 1073741823
    }

    def 'Weights class is not instantiable'() {
        when:
        new Weights()

        then:
        thrown(UnsupportedOperationException)
    }
}
