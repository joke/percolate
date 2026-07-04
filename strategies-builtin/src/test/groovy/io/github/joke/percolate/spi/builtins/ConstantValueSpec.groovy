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
class ConstantValueSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()
    @Shared def types = javac.types()

    def 'emits a zero-port operation producing a NON_NULL value for a coercible String constant'() {
        when:
        def specs = new ConstantValue().expand(Demands.withConstant(javac.STRING, 'ACTIVE'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.empty
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        types.isSameType(spec.outputType, javac.STRING)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'coerces to a primitive long target'() {
        when:
        def specs = new ConstantValue().expand(Demands.withConstant(javac.LONG, '42'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports.empty
        types.isSameType(specs[0].outputType, javac.LONG)
    }

    def 'emits nothing without a constant'() {
        expect:
        new ConstantValue().expand(Demands.forTarget(javac.STRING), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible value'() {
        expect:
        new ConstantValue().expand(Demands.withConstant(javac.INT, 'abc'), ctx).toList().empty
    }
}
