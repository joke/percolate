package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ScopeTransition
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ListWrapSpec extends Specification {

    def 'returns step for wrapping to List<E>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ListWrap().bridge(TypeUniverse.STRING, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].inputType == TypeUniverse.STRING
        steps[0].outputType == TypeUniverse.LIST_OF_STRING
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.PRESERVING
    }

    def 'returns empty when target is not List'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ListWrap().bridge(TypeUniverse.STRING, TypeUniverse.INT, ctx).toList()

        then:
        steps.empty
    }
}
