package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link WidenPrimitive} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): the widening lattice is driven entirely by stubbed seam questions
 * ({@code kind}/{@code primitiveType}) over opaque {@link TypeMirror} tokens. No javac.
 */
@Tag('unit')
class WidenPrimitiveSpec extends Specification {

    ResolveCtx ctx = Mock()

    def 'widens a numeric target from each strictly-narrower primitive, one unary operation each'() {
        TypeMirror longType = Mock()
        TypeMirror byteType = Mock()
        TypeMirror shortType = Mock()
        TypeMirror charType = Mock()
        TypeMirror intType = Mock()
        ctx.kind(longType) >> TypeKind.LONG
        ctx.primitiveType(TypeKind.BYTE) >> byteType
        ctx.primitiveType(TypeKind.SHORT) >> shortType
        ctx.primitiveType(TypeKind.CHAR) >> charType
        ctx.primitiveType(TypeKind.INT) >> intType
        byteType.kind >> TypeKind.BYTE
        shortType.kind >> TypeKind.SHORT
        charType.kind >> TypeKind.CHAR
        intType.kind >> TypeKind.INT

        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(longType), ctx).toList()

        then:
        specs.size() == 4
        specs.every { it.weight == Weights.STEP }
        specs.every { it.childScope.empty }
        specs.every { it.ports.size() == 1 && it.ports[0].name == 'value' && it.ports[0].nullness == Nullability.NON_NULL }
        specs.every { it.outputType.is(longType) && it.outputNullness == Nullability.NON_NULL }
        (specs.collect { it.ports[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR, TypeKind.INT] as Set)
    }

    def 'each widening carries a typed label using the glyph arrow'() {
        TypeMirror longType = Mock()
        TypeMirror byteType = Mock()
        TypeMirror shortType = Mock()
        TypeMirror charType = Mock()
        TypeMirror intType = Mock()
        ctx.kind(longType) >> TypeKind.LONG
        ctx.primitiveType(TypeKind.BYTE) >> byteType
        ctx.primitiveType(TypeKind.SHORT) >> shortType
        ctx.primitiveType(TypeKind.CHAR) >> charType
        ctx.primitiveType(TypeKind.INT) >> intType
        longType.toString() >> 'long'
        byteType.toString() >> 'byte'
        shortType.toString() >> 'short'
        charType.toString() >> 'char'
        intType.toString() >> 'int'

        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(longType), ctx).toList()

        then:
        (specs*.label as Set) == ['byte→long', 'short→long', 'char→long', 'int→long'] as Set
    }

    def 'includes the precision-losing long-to-double IEEE leg'() {
        TypeMirror doubleType = Mock()
        TypeMirror byteType = Mock()
        TypeMirror shortType = Mock()
        TypeMirror charType = Mock()
        TypeMirror intType = Mock()
        TypeMirror longType = Mock()
        TypeMirror floatType = Mock()
        ctx.kind(doubleType) >> TypeKind.DOUBLE
        ctx.primitiveType(TypeKind.BYTE) >> byteType
        ctx.primitiveType(TypeKind.SHORT) >> shortType
        ctx.primitiveType(TypeKind.CHAR) >> charType
        ctx.primitiveType(TypeKind.INT) >> intType
        ctx.primitiveType(TypeKind.LONG) >> longType
        ctx.primitiveType(TypeKind.FLOAT) >> floatType
        longType.kind >> TypeKind.LONG

        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(doubleType), ctx).toList()

        then:
        specs.any { it.ports[0].type.kind == TypeKind.LONG }
    }

    def 'does not widen from a wider source (no narrowing)'() {
        TypeMirror intType = Mock()
        TypeMirror byteType = Mock()
        TypeMirror shortType = Mock()
        TypeMirror charType = Mock()
        ctx.kind(intType) >> TypeKind.INT
        ctx.primitiveType(TypeKind.BYTE) >> byteType
        ctx.primitiveType(TypeKind.SHORT) >> shortType
        ctx.primitiveType(TypeKind.CHAR) >> charType
        byteType.kind >> TypeKind.BYTE
        shortType.kind >> TypeKind.SHORT
        charType.kind >> TypeKind.CHAR

        when:
        def specs = new WidenPrimitive().expand(Demands.forTarget(intType), ctx).toList()

        then:
        specs.every { it.ports[0].type.kind != TypeKind.LONG }
        (specs.collect { it.ports[0].type.kind } as Set) ==
                ([TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR] as Set)
    }

    def 'returns empty for a boolean target (no widening)'() {
        TypeMirror booleanType = Mock()
        ctx.kind(booleanType) >> TypeKind.BOOLEAN

        expect:
        new WidenPrimitive().expand(Demands.forTarget(booleanType), ctx).toList().empty
    }

    def 'returns empty for a wrapper/reference target'() {
        TypeMirror integerType = Mock()
        ctx.kind(integerType) >> TypeKind.DECLARED

        expect:
        new WidenPrimitive().expand(Demands.forTarget(integerType), ctx).toList().empty
    }

    def 'wideningStep carries the from-primitive as its input, weighted STEP, labeled with the glyph arrow'() {
        TypeMirror longType = Mock()
        TypeMirror intType = Mock()
        longType.toString() >> 'long'
        intType.toString() >> 'int'
        ctx.primitiveType(TypeKind.INT) >> intType

        expect:
        def step = WidenPrimitive.wideningStep(TypeKind.INT, longType, ctx)
        step.inputType.is(intType)
        step.weight == Weights.STEP
        step.label == 'int→long'
    }

    def 'conversions dispatches a narrower-eligible target to one step per narrower kind'() {
        TypeMirror shortType = Mock()
        TypeMirror byteType = Mock()
        ctx.kind(shortType) >> TypeKind.SHORT
        ctx.primitiveType(TypeKind.BYTE) >> byteType

        expect:
        new WidenPrimitive().conversions(shortType, ctx).toList().size() == 1
    }

    def 'conversions dispatches a target with no widening lattice entry to an empty stream'() {
        TypeMirror byteType = Mock()
        ctx.kind(byteType) >> TypeKind.BYTE

        expect:
        new WidenPrimitive().conversions(byteType, ctx).toList().empty
    }
}
