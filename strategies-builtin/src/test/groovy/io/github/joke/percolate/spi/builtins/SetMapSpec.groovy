package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class SetMapSpec extends Specification {

    def 'returns step for Iterable<E> to Set<E>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def setType = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Set'),
                TypeUniverse.STRING)

        when:
        def steps = new SetMap().bridge(TypeUniverse.LIST_OF_STRING, setType, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.LIST_OF_STRING)
        ctx.types().isSameType(steps[0].outputType, setType)
        steps[0].weight == Weights.CONTAINER
        def seed = steps[0].elementSeeds
        seed.size() == 1
        seed[0] instanceof ElementSeed
        seed[0].role == 'element'
        ctx.types().isSameType(seed[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(seed[0].outputType, TypeUniverse.STRING)
    }

    def 'returns empty when target is not Set'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new SetMap().bridge(TypeUniverse.STRING, TypeUniverse.INT, ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when source is not Iterable, array, or Optional'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def setType = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Set'),
                TypeUniverse.STRING)

        when:
        def steps = new SetMap().bridge(TypeUniverse.INTEGER, setType, ctx).toList()

        then:
        steps.empty
    }
}
