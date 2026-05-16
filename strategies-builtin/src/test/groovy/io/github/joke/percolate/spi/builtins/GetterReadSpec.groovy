package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class GetterReadSpec extends Specification {

    def 'returns empty when pathTail is null'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new GetterRead().stepsFrom(TypeUniverse.STRING, null, ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when pathTail is empty'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new GetterRead().stepsFrom(TypeUniverse.STRING, '', ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when source kind is not DECLARED'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new GetterRead().stepsFrom(TypeUniverse.INT, 'value', ctx).toList()

        then:
        steps.empty
    }

    def 'returns step through getX accessor on PersonBean'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()
        def expectedType = TypeUniverse.STRING

        when:
        def steps = new GetterRead().stepsFrom(personBean, 'name', ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].produces, expectedType)
        steps[0].weight == Weights.STEP
    }

    def 'returns step through isX accessor on BooleanBean'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def booleanBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.BooleanBean').asType()

        when:
        def steps = new GetterRead().stepsFrom(booleanBean, 'flag', ctx).toList()

        then:
        steps.size() == 1
        steps[0].produces.kind.name() == 'BOOLEAN'
        steps[0].weight == Weights.STEP
    }

    def 'returns step through field-named accessor on PersonRecord'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personRecord = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord').asType()

        when:
        def steps = new GetterRead().stepsFrom(personRecord, 'age', ctx).toList()

        then:
        steps.size() == 1
        steps[0].produces.kind == javax.lang.model.type.TypeKind.INT
        steps[0].weight == Weights.STEP
    }

    def 'returns empty when no matching accessor or field exists'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def steps = new GetterRead().stepsFrom(personBean, 'nonexistent', ctx).toList()

        then:
        steps.empty
    }
}
