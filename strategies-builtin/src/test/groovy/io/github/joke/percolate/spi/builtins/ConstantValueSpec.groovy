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
class ConstantValueSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()

    def 'emits a zero-port operation producing a NON_NULL value for a coercible String constant'() {
        when:
        def specs = new ConstantValue().expand(Demands.withConstant(TypeUniverse.STRING, 'ACTIVE'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.empty
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'coerces to a primitive long target'() {
        when:
        def specs = new ConstantValue().expand(Demands.withConstant(TypeUniverse.LONG, '42'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports.empty
        types.isSameType(specs[0].outputType, TypeUniverse.LONG)
    }

    def 'emits nothing without a constant'() {
        expect:
        new ConstantValue().expand(Demands.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible value'() {
        expect:
        new ConstantValue().expand(Demands.withConstant(TypeUniverse.INT, 'abc'), ctx).toList().empty
    }
}
