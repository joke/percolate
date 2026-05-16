package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag


@Tag('unit')
class SetWrapSpec extends Specification {

    def 'returns step for wrapping to Set<E>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def setType = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Set'),
                TypeUniverse.STRING)

        when:
        def steps = new SetWrap().bridge(TypeUniverse.STRING, setType, ctx).toList()

        then:
        steps.size() == 1
        steps[0].inputType == TypeUniverse.STRING
        steps[0].outputType == setType
        steps[0].weight == Weights.CONTAINER
        steps[0].elementSeeds.empty
    }

    def 'returns empty when target is not Set'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new SetWrap().bridge(TypeUniverse.STRING, TypeUniverse.INT, ctx).toList()

        then:
        steps.empty
    }
}
