package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag


@Tag('unit')
class ListMapSpec extends Specification {

    def 'returns step for Iterable<E> to List<E>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ListMap().bridge(TypeUniverse.LIST_OF_STRING, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.LIST_OF_STRING)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.LIST_OF_STRING)
        steps[0].weight == Weights.CONTAINER
        def seed = steps[0].elementSeeds
        seed.size() == 1
        seed[0] instanceof ElementSeed
        seed[0].role == 'element'
        ctx.types().isSameType(seed[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(seed[0].outputType, TypeUniverse.STRING)
    }

    def 'returns step for array input to List<E>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def stringArrayType = ctx.types().getArrayType(TypeUniverse.STRING)

        when:
        def steps = new ListMap().bridge(stringArrayType, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.LIST_OF_STRING)
        steps[0].weight == Weights.CONTAINER
        def seed = steps[0].elementSeeds
        seed.size() == 1
        seed[0].role == 'element'
        ctx.types().isSameType(seed[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(seed[0].outputType, TypeUniverse.STRING)
    }

    def 'returns empty when target is not List'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ListMap().bridge(TypeUniverse.STRING, TypeUniverse.INT, ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when source is not Iterable, array, or Optional'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new ListMap().bridge(TypeUniverse.INTEGER, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.empty
    }

    // FOLLOW-UP: pin current behaviour — ListMap accepts Optional<E> as input shape
    def 'pins current behaviour: ListMap accepts Optional<E> inputs'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def optionalType = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Optional'),
                TypeUniverse.STRING)

        when:
        def steps = new ListMap().bridge(optionalType, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].weight == Weights.CONTAINER
    }
}
