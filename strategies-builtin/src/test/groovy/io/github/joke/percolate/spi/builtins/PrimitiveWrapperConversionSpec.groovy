package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PrimitiveWrapperConversionSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()
    @Shared def types = javac.types()

    def 'boxes a wrapper target by consuming its primitive, one unary operation'() {
        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(javac.INTEGER), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        types.isSameType(spec.ports[0].type, javac.INT)
        spec.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(spec.outputType, javac.INTEGER)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'unboxes a primitive target by consuming its wrapper, one unary operation'() {
        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(javac.INT), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.size() == 1
        types.isSameType(spec.ports[0].type, javac.INTEGER)
        types.isSameType(spec.outputType, javac.INT)
        spec.weight == Weights.STEP
    }

    def 'returns empty for a non-wrapper, non-primitive target'() {
        expect:
        new PrimitiveWrapperConversion().expand(Demands.forTarget(javac.STRING), ctx).toList().empty
    }
}
