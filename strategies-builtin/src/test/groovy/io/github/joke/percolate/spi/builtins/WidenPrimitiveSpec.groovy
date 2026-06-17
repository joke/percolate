package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
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

    def 'widens a numeric target from each strictly-narrower primitive, one unary operation each'() {
        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(TypeUniverse.LONG), ctx).toList()

        then:
        specs.size() == 4
        specs.every { it.weight == Weights.STEP }
        specs.every { it.childScope.empty }
        specs.every { it.ports.size() == 1 && it.ports[0].name == 'value' && it.ports[0].nullness == Nullability.NON_NULL }
        specs.every { types.isSameType(it.outputType, TypeUniverse.LONG) && it.outputNullness == Nullability.NON_NULL }
        (specs.collect { it.ports[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT] as Set)
    }

    def 'each widening carries a typed label using the glyph arrow'() {
        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(TypeUniverse.LONG), ctx).toList()

        then:
        (specs*.label as Set) == ['byte→long', 'short→long', 'char→long', 'int→long'] as Set
    }

    def 'includes the precision-losing long-to-double IEEE leg'() {
        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(doubleType), ctx).toList()

        then:
        specs.any { it.ports[0].type.kind == TypeKind.LONG }
    }

    def 'does not widen from a wider source (no narrowing)'() {
        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(TypeUniverse.INT), ctx).toList()

        then:
        specs.every { it.ports[0].type.kind != TypeKind.LONG }
        (specs.collect { it.ports[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR] as Set)
    }

    def 'returns empty for a boolean target (no widening)'() {
        expect:
        new WidenPrimitive().expand(Demands.forTarget(booleanType), ctx).toList().empty
    }

    def 'returns empty for a wrapper/reference target'() {
        expect:
        new WidenPrimitive().expand(Demands.forTarget(TypeUniverse.INTEGER), ctx).toList().empty
    }
}
