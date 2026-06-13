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
class GetterPathResolverSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()

    def 'matches a getX accessor as a unary operation typed to the return type'() {
        given:
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def specs = new GetterPathResolver().expand(Demands.descend(personBean, 'name'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.STEP_GETTER
        spec.ports.size() == 1
        ctx.types().isSameType(spec.ports[0].type, personBean)
        ctx.types().isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'types the produced value through the demand nullness oracle'() {
        given:
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def specs = new GetterPathResolver().expand(Demands.descend(personBean, 'name', Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputNullness == Nullability.NULLABLE
    }

    def 'matches an isX accessor for a boolean-returning method'() {
        given:
        def booleanBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BooleanBean').asType()

        when:
        def specs = new GetterPathResolver().expand(Demands.descend(booleanBean, 'flag'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputType.kind.name() == 'BOOLEAN'
        specs[0].weight == Weights.STEP_GETTER
    }

    def 'rejects parameterized overloads when no zero-arg getter exists'() {
        given:
        def overloaded = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter').asType()

        expect:
        new GetterPathResolver().expand(Demands.descend(overloaded, 'name'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def objectType = TypeUniverse.element('java.lang.Object').asType()

        expect:
        new GetterPathResolver().expand(Demands.descend(objectType, 'class'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        expect:
        new GetterPathResolver().expand(Demands.descend(TypeUniverse.INT, 'length'), ctx).toList().empty
    }
}
