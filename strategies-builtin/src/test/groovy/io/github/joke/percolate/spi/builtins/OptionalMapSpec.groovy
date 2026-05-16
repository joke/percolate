package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag


@Tag('unit')
class OptionalMapSpec extends Specification {

    def 'returns step for Optional<E> to Optional<F>'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        // Use pre-existing types from TypeUniverse pool
        def optionalString = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Optional'),
                TypeUniverse.STRING)
        def optionalInt = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Optional'),
                TypeUniverse.INTEGER)

        when:
        def steps = new OptionalMap().bridge(optionalString, optionalInt, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, optionalString)
        ctx.types().isSameType(steps[0].outputType, optionalInt)
        steps[0].weight == Weights.CONTAINER
        def seed = steps[0].elementSeeds
        seed.size() == 1
        seed[0] instanceof ElementSeed
        seed[0].role == 'element'
        ctx.types().isSameType(seed[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(seed[0].outputType, TypeUniverse.INTEGER)
    }

    def 'returns empty when source is not Optional'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new OptionalMap().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when target is not Optional'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def optionalString = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Optional'),
                TypeUniverse.STRING)

        when:
        def steps = new OptionalMap().bridge(optionalString, TypeUniverse.INTEGER, ctx).toList()

        then:
        steps.empty
    }
}
