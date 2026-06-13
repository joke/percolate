package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PrimitiveWrapperConversionSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()

    def 'boxes a wrapper target by consuming its primitive, one unary operation'() {
        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(TypeUniverse.INTEGER), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        types.isSameType(spec.ports[0].type, TypeUniverse.INT)
        spec.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(spec.outputType, TypeUniverse.INTEGER)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'unboxes a primitive target by consuming its wrapper, one unary operation'() {
        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(TypeUniverse.INT), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.size() == 1
        types.isSameType(spec.ports[0].type, TypeUniverse.INTEGER)
        types.isSameType(spec.outputType, TypeUniverse.INT)
        spec.weight == Weights.STEP
    }

    def 'returns empty for a non-wrapper, non-primitive target'() {
        expect:
        new PrimitiveWrapperConversion().expand(Demands.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }
}
