package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class DirectAssignSpec extends Specification {

    def 'returns step for same-type assignment'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new DirectAssign().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].inputType == TypeUniverse.STRING
        steps[0].outputType == TypeUniverse.STRING
        steps[0].weight == Weights.NOOP
        steps[0].elementSeeds.empty
    }

    def 'returns empty when types are distinct'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new DirectAssign().bridge(TypeUniverse.STRING, TypeUniverse.INT, ctx).toList()

        then:
        steps.empty
    }
}
