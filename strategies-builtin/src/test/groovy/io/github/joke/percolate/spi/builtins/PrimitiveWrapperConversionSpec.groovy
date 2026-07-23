package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link PrimitiveWrapperConversion} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): boxing/unboxing/rejection is driven entirely by stubbed seam questions
 * over opaque {@link TypeMirror} tokens. No javac.
 */
@Tag('unit')
class PrimitiveWrapperConversionSpec extends Specification {

    ResolveCtx ctx = Mock()

    def 'boxes a wrapper target by consuming its primitive, one unary operation'() {
        TypeMirror integerType = Mock()
        TypeElement integerElement = Mock()
        TypeMirror intType = Mock()
        ctx.isPrimitive(integerType) >> false
        ctx.asTypeElement(integerType) >> Optional.of(integerElement)
        integerElement.qualifiedName >> nameOf('java.lang.Integer')
        ctx.unboxed(integerType) >> intType

        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(integerType), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].type.is(intType)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(integerType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
    }

    def 'unboxes a primitive target by consuming its wrapper, one unary operation'() {
        TypeMirror intType = Mock()
        TypeMirror integerType = Mock()
        ctx.isPrimitive(intType) >> true
        ctx.boxed(intType) >> integerType
        ctx.kind(intType) >> TypeKind.INT

        when:
        def specs = new PrimitiveWrapperConversion().expand(Demands.forTarget(intType), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.ports.size() == 1
        spec.ports[0].type.is(integerType)
        spec.outputType.is(intType)
        spec.weight == Weights.STEP
    }

    def 'returns empty for a non-wrapper, non-primitive target'() {
        TypeMirror stringType = Mock()
        TypeElement stringElement = Mock()
        ctx.isPrimitive(stringType) >> false
        ctx.asTypeElement(stringType) >> Optional.of(stringElement)
        stringElement.qualifiedName >> nameOf('java.lang.String')

        expect:
        new PrimitiveWrapperConversion().expand(Demands.forTarget(stringType), ctx).toList().empty
    }

    def 'box renders a $T.valueOf(...) call and carries the given primitive as its input'() {
        TypeMirror wrapperTarget = Mock()
        TypeMirror primitive = Mock()

        expect:
        def step = PrimitiveWrapperConversion.box(wrapperTarget, primitive)
        step.inputType.is(primitive)
        step.weight == Weights.STEP
    }

    def 'unbox picks the accessor named after the primitive kind and renders a chained call'() {
        TypeMirror primitiveTarget = Mock()
        TypeMirror wrapper = Mock()
        ctx.boxed(primitiveTarget) >> wrapper
        ctx.kind(primitiveTarget) >> TypeKind.LONG

        expect:
        def step = PrimitiveWrapperConversion.unbox(primitiveTarget, ctx)
        step.inputType.is(wrapper)
        step.weight == Weights.STEP
        io.github.joke.percolate.lib.javapoet.CodeBlock.of('$L\n', step.codegen.render(singleInput(io.github.joke.percolate.lib.javapoet.CodeBlock.of('$N', 'w'))))
                .toString().contains('w.longValue()')
    }

    def 'unboxedOrNull returns null when the target is not a declared type at all'() {
        TypeMirror target = Mock()
        ctx.asTypeElement(target) >> Optional.empty()

        expect:
        PrimitiveWrapperConversion.unboxedOrNull(target, ctx) == null
    }

    def 'unboxedOrNull returns null for a declared, non-wrapper type'() {
        TypeMirror target = Mock()
        TypeElement element = Mock()
        ctx.asTypeElement(target) >> Optional.of(element)
        element.qualifiedName >> nameOf('java.lang.String')

        expect:
        PrimitiveWrapperConversion.unboxedOrNull(target, ctx) == null
    }

    def 'unboxedOrNull returns the unboxed primitive for a wrapper type'() {
        TypeMirror target = Mock()
        TypeElement element = Mock()
        TypeMirror primitive = Mock()
        ctx.asTypeElement(target) >> Optional.of(element)
        element.qualifiedName >> nameOf('java.lang.Integer')
        ctx.unboxed(target) >> primitive

        expect:
        PrimitiveWrapperConversion.unboxedOrNull(target, ctx).is(primitive)
    }

    def 'conversions dispatches a primitive target to a single unbox step'() {
        TypeMirror intType = Mock()
        TypeMirror integerType = Mock()
        ctx.isPrimitive(intType) >> true
        ctx.boxed(intType) >> integerType
        ctx.kind(intType) >> TypeKind.INT

        expect:
        def steps = new PrimitiveWrapperConversion().conversions(intType, ctx).toList()
        steps.size() == 1
        steps[0].inputType.is(integerType)
    }

    def 'conversions dispatches a non-primitive, non-wrapper target to an empty stream'() {
        TypeMirror stringType = Mock()
        TypeElement stringElement = Mock()
        ctx.isPrimitive(stringType) >> false
        ctx.asTypeElement(stringType) >> Optional.of(stringElement)
        stringElement.qualifiedName >> nameOf('java.lang.String')

        expect:
        new PrimitiveWrapperConversion().conversions(stringType, ctx).toList().empty
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static io.github.joke.percolate.spi.IncomingValues singleInput(final io.github.joke.percolate.lib.javapoet.CodeBlock value) {
        [single: { -> value }] as io.github.joke.percolate.spi.IncomingValues
    }
}
