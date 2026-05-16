package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag


@Tag('unit')
class OptionalUnwrapSpec extends Specification {

    def 'returns step for Optional<E> to E'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        // Create Optional<String> type manually
        def optionalType = ctx.types().getDeclaredType(
                ctx.elements().getTypeElement('java.util.Optional'),
                TypeUniverse.STRING)
        def steps = new OptionalUnwrap().bridge(optionalType, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].inputType == optionalType
        steps[0].outputType == TypeUniverse.STRING
        steps[0].weight == Weights.CONTAINER
        steps[0].elementSeeds.empty
    }

    def 'returns empty when source is not Optional'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new OptionalUnwrap().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }
}
