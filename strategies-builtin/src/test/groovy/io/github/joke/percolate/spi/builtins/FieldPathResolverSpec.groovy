package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class FieldPathResolverSpec extends Specification {

    def 'matches a public, non-static field'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def box = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BoxFixture').asType()

        when:
        def result = new FieldPathResolver().resolve(box, 'value', ctx)

        then:
        result.present
        ctx.types().isSameType(result.get().returnType, TypeUniverse.STRING)
        result.get().weight == Weights.STEP
    }

    def 'rejects private fields'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def box = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BoxFixture').asType()

        when:
        def result = new FieldPathResolver().resolve(box, 'secret', ctx)

        then:
        !result.present
    }

    def 'rejects static fields'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def box = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BoxFixture').asType()

        when:
        def result = new FieldPathResolver().resolve(box, 'DEFAULT', ctx)

        then:
        !result.present
    }
}
