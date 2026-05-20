package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class GetterPathResolverSpec extends Specification {

    def 'matches getX accessor and returns the method return type'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def result = new GetterPathResolver().resolve(personBean, 'name', ctx)

        then:
        result.present
        ctx.types().isSameType(result.get().returnType, TypeUniverse.STRING)
        result.get().weight == Weights.STEP
    }

    def 'matches isX accessor for boolean-returning method'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def booleanBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BooleanBean').asType()

        when:
        def result = new GetterPathResolver().resolve(booleanBean, 'flag', ctx)

        then:
        result.present
        result.get().returnType.kind.name() == 'BOOLEAN'
        result.get().weight == Weights.STEP
    }

    def 'rejects parameterized overloads when no zero-arg getter exists'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def overloaded = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter').asType()

        when:
        def result = new GetterPathResolver().resolve(overloaded, 'name', ctx)

        then:
        !result.present
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def objectType = TypeUniverse.element('java.lang.Object').asType()

        when:
        def result = new GetterPathResolver().resolve(objectType, 'class', ctx)

        then:
        !result.present
    }

    def 'returns empty for non-declared parent types'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def result = new GetterPathResolver().resolve(TypeUniverse.INT, 'length', ctx)

        then:
        !result.present
    }
}
