package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
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
        def steps = new FieldPathResolver().expand(Frontiers.descend(box, 'value'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
        steps[0].weight == Weights.STEP_FIELD
    }

    def 'rejects private fields'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def box = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BoxFixture').asType()

        expect:
        new FieldPathResolver().expand(Frontiers.descend(box, 'secret'), ctx).toList().empty
    }

    def 'rejects static fields'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def box = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BoxFixture').asType()

        expect:
        new FieldPathResolver().expand(Frontiers.descend(box, 'DEFAULT'), ctx).toList().empty
    }
}
