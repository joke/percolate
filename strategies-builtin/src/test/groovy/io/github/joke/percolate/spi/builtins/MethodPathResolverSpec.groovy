package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MethodPathResolverSpec extends Specification {

    def 'matches a canonical record accessor'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def point = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.Point').asType()

        when:
        def result = new MethodPathResolver().resolve(point, 'x', ctx)

        then:
        result.present
        result.get().returnType.kind.name() == 'INT'
        result.get().weight == Weights.STEP_METHOD
    }

    def 'matches a non-record fluent-style accessor'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def address = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.AddressFluent').asType()

        when:
        def result = new MethodPathResolver().resolve(address, 'street', ctx)

        then:
        result.present
        ctx.types().isSameType(result.get().returnType, TypeUniverse.STRING)
        result.get().weight == Weights.STEP_METHOD
    }

    def 'rejects parameterised methods of the same name'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def overloaded = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter').asType()

        when:
        def result = new MethodPathResolver().resolve(overloaded, 'getName', ctx)

        then:
        !result.present
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def objectType = TypeUniverse.element('java.lang.Object').asType()

        when:
        def result = new MethodPathResolver().resolve(objectType, 'toString', ctx)

        then:
        !result.present
    }

    def 'returns empty for non-declared parent types'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def result = new MethodPathResolver().resolve(TypeUniverse.INT, 'length', ctx)

        then:
        !result.present
    }
}
