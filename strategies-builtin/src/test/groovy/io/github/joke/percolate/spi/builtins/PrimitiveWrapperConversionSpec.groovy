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

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
