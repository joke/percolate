package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ConstructorCallSpec extends Specification {

    def 'returns empty when targetTails is null'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personRecord = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord').asType()

        when:
        def result = new ConstructorCall().buildFor(personRecord, null, ctx)

        then:
        !result.present
    }

    def 'returns empty when targetTails is empty'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personRecord = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord').asType()

        when:
        def result = new ConstructorCall().buildFor(personRecord, [], ctx)

        then:
        !result.present
    }

    def 'returns empty when target type is not DECLARED'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def result = new ConstructorCall().buildFor(TypeUniverse.INT, ['name'], ctx)

        then:
        !result.present
    }

    def 'returns GroupBuild through constructor-by-parameter-name for PersonRecord'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personRecord = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord').asType()

        when:
        def result = new ConstructorCall().buildFor(personRecord, ['age', 'name'], ctx)

        then:
        result.present
        def build = result.get()
        build.slots.size() == 2
        build.slots[0].name == 'age'
        build.slots[0].type.kind == javax.lang.model.type.TypeKind.INT
        build.slots[0].weight == Weights.STEP
        build.slots[1].name == 'name'
        ctx.types().isSameType(build.slots[1].type, TypeUniverse.STRING)
        build.slots[1].weight == Weights.STEP
    }

    def 'returns GroupBuild through constructor-by-arity-and-fields for PersonByFieldOrder'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personByFieldOrder = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonByFieldOrder').asType()

        when:
        def result = new ConstructorCall().buildFor(personByFieldOrder, ['age', 'name'], ctx)

        then:
        result.present
        def build = result.get()
        build.slots.size() == 2
        build.slots[0].name == 'age'
        build.slots[0].type.kind == javax.lang.model.type.TypeKind.INT
        build.slots[0].weight == Weights.STEP
        build.slots[1].name == 'name'
        ctx.types().isSameType(build.slots[1].type, TypeUniverse.STRING)
        build.slots[1].weight == Weights.STEP
    }
}
