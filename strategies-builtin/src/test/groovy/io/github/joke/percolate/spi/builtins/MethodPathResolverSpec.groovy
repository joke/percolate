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
class MethodPathResolverSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()

    def 'matches a canonical record accessor as a unary operation typed to the return type'() {
        given:
        def point = javac.of(io.github.joke.percolate.spi.builtins.fixtures.Point).asType()

        when:
        def specs = new MethodPathResolver().descend(Demands.descend(point, 'x'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.outputType.kind.name() == 'INT'
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP_METHOD
        spec.ports.size() == 1
        ctx.types().isSameType(spec.ports[0].type, point)
    }

    def 'matches a non-record fluent-style accessor'() {
        given:
        def address = javac.of(io.github.joke.percolate.spi.builtins.fixtures.AddressFluent).asType()

        when:
        def specs = new MethodPathResolver().descend(Demands.descend(address, 'street'), ctx).toList()

        then:
        specs.size() == 1
        ctx.types().isSameType(specs[0].outputType, javac.STRING)
        specs[0].weight == Weights.STEP_METHOD
    }

    def 'rejects parameterised methods of the same name'() {
        given:
        def overloaded = javac.of(io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter).asType()

        expect:
        new MethodPathResolver().descend(Demands.descend(overloaded, 'getName'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def objectType = javac.element('java.lang.Object').asType()

        expect:
        new MethodPathResolver().descend(Demands.descend(objectType, 'toString'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        expect:
        new MethodPathResolver().descend(Demands.descend(javac.INT, 'length'), ctx).toList().empty
    }
}
