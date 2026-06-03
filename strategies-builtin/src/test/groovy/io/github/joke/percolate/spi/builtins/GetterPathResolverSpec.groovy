package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class GetterPathResolverSpec extends Specification {

    def 'matches getX accessor and emits a BOUNDARY step typed to the return type'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def steps = new GetterPathResolver().expand(Frontiers.descend(personBean, 'name'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
        steps[0].weight == Weights.STEP_GETTER
        ctx.types().isSameType(steps[0].inputs[0].type, personBean)
    }

    def 'matches isX accessor for boolean-returning method'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def booleanBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BooleanBean').asType()

        when:
        def steps = new GetterPathResolver().expand(Frontiers.descend(booleanBean, 'flag'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].output.kind.name() == 'BOOLEAN'
        steps[0].weight == Weights.STEP_GETTER
    }

    def 'rejects parameterized overloads when no zero-arg getter exists'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def overloaded = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.OverloadedGetter').asType()

        expect:
        new GetterPathResolver().expand(Frontiers.descend(overloaded, 'name'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def objectType = TypeUniverse.element('java.lang.Object').asType()

        expect:
        new GetterPathResolver().expand(Frontiers.descend(objectType, 'class'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        expect:
        new GetterPathResolver().expand(Frontiers.descend(TypeUniverse.INT, 'length'), ctx).toList().empty
    }
}
