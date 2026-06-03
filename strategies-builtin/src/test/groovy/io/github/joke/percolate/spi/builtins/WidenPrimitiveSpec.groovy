package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeKind

@Tag('unit')
class WidenPrimitiveSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()
    def doubleType = types.getPrimitiveType(TypeKind.DOUBLE)
    def booleanType = types.getPrimitiveType(TypeKind.BOOLEAN)

    def 'widens a numeric target from each strictly-narrower primitive'() {
        when:
        def steps = new WidenPrimitive().expand(Frontiers.forTarget(TypeUniverse.LONG), ctx).toList()

        then:
        steps.size() == 4
        steps.every { it.intent == Intent.CONVERSION && it.weight == Weights.STEP }
        steps.every { types.isSameType(it.output, TypeUniverse.LONG) }
        (steps.collect { it.inputs[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT] as Set)
    }

    def 'includes the precision-losing long-to-double IEEE leg'() {
        when:
        def steps = new WidenPrimitive().expand(Frontiers.forTarget(doubleType), ctx).toList()

        then:
        steps.any { it.inputs[0].type.kind == TypeKind.LONG }
    }

    def 'does not widen from a wider source (no narrowing)'() {
        when:
        def steps = new WidenPrimitive().expand(Frontiers.forTarget(TypeUniverse.INT), ctx).toList()

        then:
        steps.every { it.inputs[0].type.kind != TypeKind.LONG }
        (steps.collect { it.inputs[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR] as Set)
    }

    def 'returns empty for a boolean target (no widening)'() {
        expect:
        new WidenPrimitive().expand(Frontiers.forTarget(booleanType), ctx).toList().empty
    }

    def 'returns empty for a wrapper/reference target'() {
        expect:
        new WidenPrimitive().expand(Frontiers.forTarget(TypeUniverse.INTEGER), ctx).toList().empty
    }
}
