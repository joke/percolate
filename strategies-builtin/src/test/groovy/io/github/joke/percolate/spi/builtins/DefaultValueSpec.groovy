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
class DefaultValueSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()
    def optionalOfString = types.getDeclaredType(TypeUniverse.element('java.util.Optional'), TypeUniverse.STRING)

    def 'fires only with a present default'() {
        expect:
        new DefaultValue().expand(Demands.forTarget(TypeUniverse.STRING, [TypeUniverse.STRING]), ctx).toList().empty
    }

    def 'coalesces a nullable reference scalar via a unary NOOP operation over a NULLABLE port'() {
        when:
        def specs = new DefaultValue()
                .expand(Demands.withDefault(TypeUniverse.STRING, 'unknown', [TypeUniverse.STRING]), ctx)
                .toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        types.isSameType(spec.ports[0].type, TypeUniverse.STRING)
        spec.ports[0].nullness == Nullability.NULLABLE
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'coalesces an Optional source via a unary operation over a present Optional port'() {
        when:
        def specs = new DefaultValue()
                .expand(Demands.withDefault(TypeUniverse.STRING, 'unknown', [optionalOfString]), ctx)
                .toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        types.isSameType(spec.ports[0].type, optionalOfString)
        spec.ports[0].nullness == Nullability.NON_NULL
        types.isSameType(spec.outputType, TypeUniverse.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'coerces the default literal to a wrapper target type'() {
        when:
        def specs = new DefaultValue()
                .expand(Demands.withDefault(TypeUniverse.INTEGER, '0', [TypeUniverse.INTEGER]), ctx)
                .toList()

        then:
        specs.size() == 1
        types.isSameType(specs[0].outputType, TypeUniverse.INTEGER)
        specs[0].ports[0].nullness == Nullability.NULLABLE
    }

    def 'emits nothing for a primitive source (a primitive can never be absent)'() {
        expect:
        new DefaultValue().expand(Demands.withDefault(TypeUniverse.INT, '0', [TypeUniverse.INT]), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible default'() {
        expect:
        new DefaultValue().expand(Demands.withDefault(TypeUniverse.INT, 'abc', [TypeUniverse.INT]), ctx).toList().empty
    }
}
