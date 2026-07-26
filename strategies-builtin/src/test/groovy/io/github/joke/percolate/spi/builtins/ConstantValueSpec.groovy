package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Offer
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Subjects
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
        def offers = new ConstantValue().expand(Demands.withConstant(longType, '42'), ctx).toList()

        then:
        offers.size() == 1
        def spec = offers[0].spec
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
        def offers = new ConstantValue().expand(Demands.withConstant(intType, '7'), ctx).toList()

        then:
        offers.size() == 1
        offers[0].spec.ports.empty
        offers[0].spec.outputType.is(intType)
    }

    def 'the label and rendered codegen are the exact coerced literal text'() {
        intType.kind >> TypeKind.INT

        when:
        def offers = new ConstantValue().expand(Demands.withConstant(intType, '7'), ctx).toList()

        then:
        offers[0].spec.label == '7'
        offers[0].spec.codegen.render(null).toString() == '7'
    }

    def 'constantSpec wires a zero-port, STEP-weighted, NON_NULL spec whose label is the literal text'() {
        def literal = io.github.joke.percolate.lib.javapoet.CodeBlock.of('42L')

        expect:
        def spec = ConstantValue.constantSpec(longType, literal, DirectiveInput.scalar('constant', '42', Subjects.none()))
        spec.label == '42L'
        spec.ports.empty
        spec.outputType.is(longType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
        spec.codegen.render(null).toString() == '42L'
    }

    def 'emits nothing (silence, not refusal) without a constant declared'() {
        expect:
        new ConstantValue().expand(Demands.forTarget(longType), ctx).toList().empty
    }

    def 'refuses an uncoercible constant, naming the offending literal and target'() {
        intType.kind >> TypeKind.INT
        intType.toString() >> 'int'

        when:
        def offers = new ConstantValue().expand(Demands.withConstant(intType, 'abc'), ctx).toList()

        then:
        offers.size() == 1
        def refusal = offers[0]
        refusal instanceof Offer.Refusal
        refusal.subject.is(Subjects.none())
        refusal.message == "cannot coerce 'abc' to int"
    }
}
