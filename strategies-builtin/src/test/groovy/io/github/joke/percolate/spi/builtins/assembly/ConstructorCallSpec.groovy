package io.github.joke.percolate.spi.builtins.assembly

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ProduceDemand
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.ElementVisitor
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
    ConstructorCall constructorCall = new ConstructorCall()
    TypeMirror targetType = Mock()
    TypeElement typeElement = Mock()

    def 'emits no operation when the target type is not DECLARED'() {
        ctx.asTypeElement(targetType) >> Optional.empty()

        expect: 'declared children are present, so only the missing element can stop the assembly'
        constructorCall.expand(Demands.assembling(targetType, ['number'] as Set), ctx).toList().empty
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
        numberType.toString() >> 'int'
        streetType.toString() >> 'String'
        // ClassName.get(TypeElement) resolves the owning package through the enclosing element's visitor.
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Address')
        typeElement.simpleName >> nameOf('Address')
        typeElement.enclosingElement >> enclosing
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        def declared = ['number', 'street'] as Set
        def specs = constructorCall.expand(Demands.assembling(targetType, declared), ctx)*.spec

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
        spec.ports.every { it.subTarget }
        spec.ports[0].type.is(numberType)
        spec.ports[1].type.is(streetType)
        spec.ports.every { it.nullness == Nullability.NON_NULL }
        spec.label == 'new Address(int, String)'

        and: 'the codegen names the target type and feeds each port by name, in parameter order'
        spec.codegen.render(byName([number: CodeBlock.of('$N', 'n'), street: CodeBlock.of('$N', 's')])).toString()
                == 'new com.example.Address(n, s)'
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
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect:
        constructorCall.expand(Demands.assembling(targetType, ['nonexistent'] as Set), ctx).toList().empty
    }

    def 'rejects a private constructor'() {
        ExecutableElement ctor = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(ctor)
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> true
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect:
        constructorCall.expand(Demands.assembling(targetType, ['x'] as Set), ctx).toList().empty
    }

    def 'a leaf demand (no declared children) is never assembled, even for a declared target'() {
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)

        expect:
        constructorCall.expand(Demands.forTarget(targetType), ctx).toList().empty
    }

    def 'rejects a non-constructor member'() {
        ExecutableElement method = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(method)
        ctx.isConstructor(method) >> false
        ctx.option('percolate.construction.preference') >> Optional.empty()

        expect:
        constructorCall.expand(Demands.assembling(targetType, ['x'] as Set), ctx).toList().empty
    }

    // candidateConstructor is the whole gate in one place: each rejection yields an empty stream of its own, never
    // a null the surrounding flatMap would silently absorb.
    def 'candidateConstructor yields the constructor itself when it is non-private and its parameters are the declared children'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        ctor.parameters >> [numberParam]
        numberParam.simpleName >> nameOf('number')

        expect:
        constructorCall.candidateConstructor(ctor, ['number'] as Set, ctx).toList() == [ctor]
    }

    def 'candidateConstructor yields nothing for a member that is not a constructor'() {
        Element member = Mock()
        ctx.isConstructor(member) >> false

        expect:
        constructorCall.candidateConstructor(member, ['number'] as Set, ctx).toList().empty
    }

    def 'candidateConstructor yields nothing for a private constructor'() {
        ExecutableElement ctor = Mock()
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> true

        expect:
        constructorCall.candidateConstructor(ctor, ['number'] as Set, ctx).toList().empty
    }

    def 'candidateConstructor yields nothing when the parameter names are not exactly the declared children'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false
        ctor.parameters >> [numberParam]
        numberParam.simpleName >> nameOf('number')

        expect:
        constructorCall.candidateConstructor(ctor, ['street'] as Set, ctx).toList().empty
    }

    def 'parameterNames collects each constructor parameter simple name into an unordered set'() {
        ExecutableElement ctor = Mock()
        VariableElement numberParam = Mock()
        VariableElement streetParam = Mock()
        ctor.parameters >> [numberParam, streetParam]
        numberParam.simpleName >> nameOf('number')
        streetParam.simpleName >> nameOf('street')

        expect:
        constructorCall.parameterNames(ctor) == ['number', 'street'] as Set
    }

    def 'parameterNames is empty for a zero-arg constructor'() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> []

        expect:
        constructorCall.parameterNames(ctor).empty
    }

    def 'constructorLabel composes new TypeName(portType, portType, ...) from the simple names'() {
        TypeMirror intType = Mock()
        intType.toString() >> 'int'
        def port = Port.subTarget('number', intType, Nullability.NON_NULL)
        typeElement.simpleName >> nameOf('Address')

        expect:
        constructorCall.constructorLabel(typeElement, [port]) == 'new Address(int)'
    }

    // The cast to ExecutableElement is load-bearing: a member the seam reports as a constructor but that is not
    // executable is a broken ResolveCtx, and it must fail loudly rather than be skipped like an ordinary mismatch.
    def 'a non-executable member reported as a constructor fails rather than being skipped'() {
        Element member = Mock()
        ctx.asTypeElement(targetType) >> Optional.of(typeElement)
        ctx.membersOf(typeElement) >> Stream.of(member)
        ctx.isConstructor(member) >> true
        ctx.isPrivate(member) >> true
        ctx.option('percolate.construction.preference') >> Optional.empty()

        when:
        constructorCall.expand(Demands.assembling(targetType, ['x'] as Set), ctx).toList()

        then:
        thrown(ClassCastException)
    }

    // buildSpec asks the demand for each parameter's nullness with that parameter's own type and element.
    def 'buildSpec reads every port nullness from the demand, per parameter'() {
        ExecutableElement ctor = Stub()
        VariableElement numberParam = Stub()
        TypeMirror numberType = Stub()
        TypeElement element = Stub()
        ProduceDemand demand = Mock()
        ctor.parameters >> [numberParam]
        numberParam.simpleName >> nameOf('number')
        numberParam.asType() >> numberType
        element.simpleName >> nameOf('Address')

        when:
        def spec = constructorCall.buildSpec(ctor, element, targetType, demand, Weights.STEP)

        then:
        1 * demand.nullnessOf(numberType, numberParam) >> Nullability.NULLABLE
        0 * _

        expect:
        spec.ports[0].nullness == Nullability.NULLABLE
    }

    // ---- weight: the strategy prices itself from percolate.construction.preference (design D4) --------------

    def 'weight is STEP when the preference is absent, so the constructor is preferred by default'() {
        when:
        def weight = constructorCall.weight(ctx)

        then:
        1 * ctx.option('percolate.construction.preference') >> Optional.empty()
        0 * _

        expect:
        weight == Weights.STEP
    }

    def 'weight is STEP when the preference names the constructor'() {
        when:
        def weight = constructorCall.weight(ctx)

        then:
        1 * ctx.option('percolate.construction.preference') >> Optional.of('constructor')
        0 * _

        expect:
        weight == Weights.STEP
    }

    def 'weight is EXPENSIVE when the preference names the builder, so a builder outbids it'() {
        when:
        def weight = constructorCall.weight(ctx)

        then:
        1 * ctx.option('percolate.construction.preference') >> Optional.of('builder')
        0 * _

        expect:
        weight == Weights.EXPENSIVE
    }

    /** An {@link IncomingValues} resolving each port by its slot name, as an assembly operation does. */
    private static IncomingValues byName(final Map<String, CodeBlock> values) {
        [byName: { String slot -> values[slot] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
