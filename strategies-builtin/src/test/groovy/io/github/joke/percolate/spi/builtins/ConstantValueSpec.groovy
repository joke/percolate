package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link ConstantValue} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): the strategy asks the seam no questions — it delegates coercion to
 * {@link io.github.joke.percolate.spi.LiteralCoercion} — so the mocked {@code ResolveCtx} stays unstubbed. The
 * target {@link TypeMirror} answers only {@code getKind()}, the raw JLS-model plumbing {@code LiteralCoercion}
 * itself reads (never a {@code ResolveCtx} seam question), and is otherwise never interrogated.
 */
@Tag('unit')
class ConstantValueSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror longType = Mock()
    TypeMirror intType = Mock()

    def 'emits a zero-port operation producing a NON_NULL value for a coercible long constant'() {
        longType.kind >> TypeKind.LONG

        when:
        def specs = new ConstantValue().expand(Demands.withConstant(longType, '42'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.empty
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.outputType.is(longType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'coerces to a different primitive target'() {
        intType.kind >> TypeKind.INT

        when:
        def specs = new ConstantValue().expand(Demands.withConstant(intType, '7'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports.empty
        specs[0].outputType.is(intType)
    }

    def 'the label and rendered codegen are the exact coerced literal text'() {
        intType.kind >> TypeKind.INT

        when:
        def specs = new ConstantValue().expand(Demands.withConstant(intType, '7'), ctx).toList()

        then:
        specs[0].label == '7'
        specs[0].codegen.render(null).toString() == '7'
    }

    def 'constantSpec wires a zero-port, STEP-weighted, NON_NULL spec whose label is the literal text'() {
        def literal = io.github.joke.percolate.javapoet.CodeBlock.of('42L')

        expect:
        def spec = ConstantValue.constantSpec(longType, literal)
        spec.label == '42L'
        spec.ports.empty
        spec.outputType.is(longType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
        spec.codegen.render(null).toString() == '42L'
    }

    def 'emits nothing without a constant'() {
        expect:
        new ConstantValue().expand(Demands.forTarget(longType), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible value'() {
        intType.kind >> TypeKind.INT

        expect:
        new ConstantValue().expand(Demands.withConstant(intType, 'abc'), ctx).toList().empty
    }
}
