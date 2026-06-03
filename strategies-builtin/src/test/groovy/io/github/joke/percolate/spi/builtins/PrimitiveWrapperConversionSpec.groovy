package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PrimitiveWrapperConversionSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()

    def 'boxes a wrapper target by consuming its primitive'() {
        when:
        def steps = new PrimitiveWrapperConversion().expand(Frontiers.forTarget(TypeUniverse.INTEGER), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.CONVERSION
        steps[0].scope.empty
        steps[0].inputs.size() == 1
        types.isSameType(steps[0].inputs[0].type, TypeUniverse.INT)
        types.isSameType(steps[0].output, TypeUniverse.INTEGER)
        steps[0].weight == Weights.STEP
    }

    def 'unboxes a primitive target by consuming its wrapper'() {
        when:
        def steps = new PrimitiveWrapperConversion().expand(Frontiers.forTarget(TypeUniverse.INT), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.CONVERSION
        steps[0].inputs.size() == 1
        types.isSameType(steps[0].inputs[0].type, TypeUniverse.INTEGER)
        types.isSameType(steps[0].output, TypeUniverse.INT)
        steps[0].weight == Weights.STEP
    }

    def 'returns empty for a non-wrapper, non-primitive target'() {
        expect:
        new PrimitiveWrapperConversion().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }
}
