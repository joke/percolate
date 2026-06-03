package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
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
        def steps = new MethodPathResolver().expand(Frontiers.descend(point, 'x'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        steps[0].output.kind.name() == 'INT'
        steps[0].weight == Weights.STEP_METHOD
    }

    def 'matches a non-record fluent-style accessor'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def address = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.AddressFluent').asType()

        when:
        def steps = new MethodPathResolver().expand(Frontiers.descend(address, 'street'), ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
        steps[0].weight == Weights.STEP_METHOD
    }

    def 'rejects parameterised methods of the same name'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def overloaded = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter').asType()

        expect:
        new MethodPathResolver().expand(Frontiers.descend(overloaded, 'getName'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def objectType = TypeUniverse.element('java.lang.Object').asType()

        expect:
        new MethodPathResolver().expand(Frontiers.descend(objectType, 'toString'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        expect:
        new MethodPathResolver().expand(Frontiers.descend(TypeUniverse.INT, 'length'), ctx).toList().empty
    }
}
