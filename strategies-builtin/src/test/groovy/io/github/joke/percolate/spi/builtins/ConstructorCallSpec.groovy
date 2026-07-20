package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link ConstructorCall} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): member reflection is stubbed on a mocked {@code ResolveCtx} over opaque
 * {@link ExecutableElement}/{@link VariableElement} member tokens. No javac, no {@code ResolveCtxBuilder}, no shape
 * fixtures.
 */
@Tag('unit')
class ConstructorCallSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror targetType = Mock()
    TypeElement typeElement = Mock()

    def 'emits no operation when the target type is not DECLARED'() {
        ctx.asTypeElement(targetType) >> Optional.empty()

        expect:
        new ConstructorCall().expand(Demands.forTarget(targetType), ctx).toList().empty
    }

    def 'emits a multi-port assembly operation over the constructor parameters, in declaration order, when the goal spec matches'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        VariableElement streetParam = Mock()
        TypeMirror numberType = Mock()
        TypeMirror streetType = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(ctor)
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        ctor.parameters >> [numberParam, streetParam]
        numberParam.simpleName >> nameOf('number')
        streetParam.simpleName >> nameOf('street')
        numberParam.asType() >> numberType
        streetParam.asType() >> streetType

        when:
        def declared = ['number', 'street'] as Set
        def specs = new ConstructorCall().expand(Demands.assembling(targetType, declared), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.outputType.is(targetType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP
        spec.ports.size() == 2
        (spec.ports*.name as Set) == declared
        spec.ports.every { it.sourcing == Port.Sourcing.SUBTARGET }
        spec.ports[0].type.is(numberType)
        spec.ports[1].type.is(streetType)
    }

    def 'rejects a constructor whose parameters do not match the declared-children goal spec'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        VariableElement streetParam = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(ctor)
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        ctor.parameters >> [numberParam, streetParam]
        numberParam.simpleName >> nameOf('number')
        streetParam.simpleName >> nameOf('street')

        expect:
        new ConstructorCall().expand(Demands.assembling(targetType, ['nonexistent'] as Set), ctx).toList().empty
    }

    def 'rejects a private constructor'() {
        ExecutableElement ctor = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(ctor)
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> true

        expect:
        new ConstructorCall().expand(Demands.assembling(targetType, ['x'] as Set), ctx).toList().empty
    }

    def 'a leaf demand (no declared children) is never assembled, even for a declared target'() {
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)

        expect:
        new ConstructorCall().expand(Demands.forTarget(targetType), ctx).toList().empty
    }

    def 'rejects a non-constructor member'() {
        ExecutableElement method = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(method)
        ctx.isConstructor(method) >> false

        expect:
        new ConstructorCall().expand(Demands.assembling(targetType, ['x'] as Set), ctx).toList().empty
    }

    def 'parameterNames collects each constructor parameter simple name into an unordered set'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        VariableElement streetParam = Mock()
        ctor.parameters >> [numberParam, streetParam]
        numberParam.simpleName >> nameOf('number')
        streetParam.simpleName >> nameOf('street')

        expect:
        ConstructorCall.parameterNames(ctor) == ['number', 'street'] as Set
    }

    def 'parameterNames is empty for a zero-arg constructor'() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> []

        expect:
        ConstructorCall.parameterNames(ctor).empty
    }

    def 'constructorLabel composes new TypeName(portType, portType, ...) from the simple names'() {
        TypeMirror intType = Mock()
        intType.toString() >> 'int'
        def port = Port.subTarget('number', intType, Nullability.NON_NULL)
        typeElement.simpleName >> nameOf('Address')

        expect:
        ConstructorCall.constructorLabel(typeElement, [port]) == 'new Address(int)'
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
