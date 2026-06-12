package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.Renders
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ConstantValueSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()

    def 'emits a zero-input BOUNDARY rendering the coerced String literal'() {
        when:
        def steps = new ConstantValue().expand(Frontiers.withConstant(TypeUniverse.STRING, 'ACTIVE'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        steps[0].inputs.empty
        types.isSameType(steps[0].output, TypeUniverse.STRING)
        steps[0].weight == Weights.STEP
        Renders.edge(steps[0].codegen, 'ignored') == '"ACTIVE"'
    }

    def 'coerces to a primitive long target literal'() {
        when:
        def steps = new ConstantValue().expand(Frontiers.withConstant(TypeUniverse.LONG, '42'), ctx).toList()

        then:
        steps.size() == 1
        steps[0].inputs.empty
        types.isSameType(steps[0].output, TypeUniverse.LONG)
        Renders.edge(steps[0].codegen, 'ignored') == '42L'
    }

    def 'emits nothing without a constant'() {
        expect:
        new ConstantValue().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible value'() {
        expect:
        new ConstantValue().expand(Frontiers.withConstant(TypeUniverse.INT, 'abc'), ctx).toList().empty
    }
}
