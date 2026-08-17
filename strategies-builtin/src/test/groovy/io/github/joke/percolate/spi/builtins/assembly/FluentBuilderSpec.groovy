package io.github.joke.percolate.spi.builtins.assembly

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
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
 * {@link FluentBuilder} — the baseline builder convention — unit-tested mock-only over the {@link ResolveCtx}
 * type-query seam. Member reflection is stubbed over opaque member tokens; no javac, no {@code ResolveCtxBuilder}.
 *
 * <p>Because {@code FluentBuilder} is the baseline, this spec also exercises the {@link BuilderAssembly} shape every
 * convention shares: the containment gate, the terminal {@code build()} check, and the one-operation emission. The
 * sibling specs cover only what their own convention varies.
 *
 * <p>The gate under test is <b>containment</b>, not the equality {@code ConstructorCall} uses: a builder offering
 * more setters than the demand declares still applies, and only the declared children become ports.
 */
@Tag('unit')
class FluentBuilderSpec extends Specification {

    ResolveCtx ctx = Mock()
    FluentBuilder fluentBuilder = new FluentBuilder()
    TypeMirror targetType = Mock()
    TypeElement targetElement = Mock()
    TypeElement builderElement = Mock()
    TypeMirror builderType = Mock()

    def 'emits no operation when the target type is not DECLARED'() {
        ctx.asTypeElement(targetType) >> Optional.empty()

        expect:
        fluentBuilder.expand(Demands.assembling(targetType, ['name'] as Set), ctx).toList().empty
    }

    def 'a leaf demand (no declared children) is never assembled through a builder'() {
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)

        expect:
        fluentBuilder.expand(Demands.forTarget(targetType), ctx).toList().empty
    }

    def 'emits one n-ary operation whose sub-target ports are the declared children, in declared order'() {
        stubWholeBuilder(['name', 'age'])
        ctx.option('percolate.construction.preference') >> Optional.empty()

        def specs = fluentBuilder.expand(Demands.assembling(targetType, ['name', 'age'] as Set), ctx)*.spec

        expect:
        specs.size() == 1
        verifyAll(specs[0]) {
            childScope.empty
            codegen instanceof OperationCodegen
            outputType.is(targetType)
            outputNullness == Nullability.NON_NULL
            ports.size() == 2
            ports*.name == ['name', 'age']
            ports.every { it.subTarget }
            weight == Weights.EXPENSIVE
            label == 'Person.builder(String, String).build()'
        }
    }

    def 'renders one chained expression carrying a wrap marker at every continuation'() {
        stubWholeBuilder(['name'])
        ctx.option('percolate.construction.preference') >> Optional.empty()

        def spec = fluentBuilder.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec.first()
        def rendered = CodeBlock.of('$L\n', spec.codegen.render(byName([name: CodeBlock.of('$N', 'n')]))).toString()

        expect:
        rendered.contains('com.example.Person.builder()')
        rendered.contains('.name(n)')
        rendered.contains('.build()')
    }

    // ---- the containment gate ------------------------------------------------------------------------------

    def 'setters matches when the declared children are a strict subset of the builder setters'() {
        stubSetters(['name', 'age', 'email'])

        expect:
        fluentBuilder.setters(builderElement, ['name', 'age'], ctx).get()*.simpleName*.toString() == ['name', 'age']
    }

    def 'setters yields nothing when a declared child has no matching setter'() {
        stubSetters(['name'])

        expect:
        fluentBuilder.setters(builderElement, ['name', 'nickname'], ctx).empty
    }

    def 'setter rejects a method that does not return the builder'() {
        def notFluent = singleArgMethod('name', targetType)
        ctx.membersOf(builderElement) >> { Stream.of(notFluent) }
        ctx.isMethod(notFluent) >> true
        ctx.isPrivate(notFluent) >> false
        builderElement.asType() >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.erasure(builderType) >> builderType
        ctx.isAssignable(targetType, builderType) >> false

        expect:
        fluentBuilder.setter(builderElement, 'name', ctx).empty
    }

    def 'returnsBuilder compares the erased setter return against the erased builder'() {
        TypeMirror setterReturn = Mock()
        TypeMirror erased = Mock()
        def setter = singleArgMethod('name', setterReturn)
        builderElement.asType() >> builderType
        ctx.erasure(setterReturn) >> erased
        ctx.erasure(builderType) >> erased
        ctx.isAssignable(erased, erased) >> true
        ctx.isAssignable(setterReturn, erased) >> false

        expect:
        fluentBuilder.returnsBuilder(setter, builderElement, ctx)
    }

    // ---- entry point and terminal build() ---------------------------------------------------------------------

    def 'builderFor yields the type the static builder() factory returns'() {
        def entry = noArgMethod('builder', builderType)
        def other = noArgMethod('toString', targetType)
        ctx.membersOf(targetElement) >> { Stream.of(other, entry) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        ctx.isStatic(entry) >> true
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)

        expect:
        fluentBuilder.builderFor(targetElement, ctx).get().is(builderElement)
    }

    def 'builderFor yields nothing when the factory is not static'() {
        def entry = noArgMethod('builder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> false
        ctx.isStatic(entry) >> false

        expect:
        fluentBuilder.builderFor(targetElement, ctx).empty
    }

    def 'builderFor yields nothing when the factory is private'() {
        def entry = noArgMethod('builder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> true

        expect:
        fluentBuilder.builderFor(targetElement, ctx).empty
    }

    def 'builderFor yields nothing when no member carries the factory name'() {
        def other = noArgMethod('toString', targetType)
        ctx.membersOf(targetElement) >> { Stream.of(other) }
        ctx.isMethod(other) >> true
        ctx.isPrivate(other) >> false
        ctx.isStatic(other) >> true

        expect:
        fluentBuilder.builderFor(targetElement, ctx).empty
    }

    def 'builderFor yields nothing when the returned builder type is private'() {
        def entry = noArgMethod('builder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> false
        ctx.isStatic(entry) >> true
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)
        ctx.isPrivate(builderElement) >> true

        expect:
        fluentBuilder.builderFor(targetElement, ctx).empty
    }

    def 'hasBuild compares the erased build return against the erased target'() {
        TypeMirror buildReturn = Mock()
        TypeMirror erased = Mock()
        def build = noArgMethod('build', buildReturn)
        def other = noArgMethod('toString', targetType)
        ctx.membersOf(builderElement) >> { Stream.of(other, build) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        ctx.erasure(buildReturn) >> erased
        ctx.erasure(targetType) >> erased
        ctx.isAssignable(erased, erased) >> true
        ctx.isAssignable(buildReturn, erased) >> false

        expect:
        fluentBuilder.hasBuild(builderElement, targetType, ctx)
    }

    def 'hasBuild is false when an assignable method is not named build'() {
        def other = noArgMethod('toString', targetType)
        ctx.membersOf(builderElement) >> { Stream.of(other) }
        ctx.isMethod(other) >> true
        ctx.isPrivate(other) >> false
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(targetType, targetType) >> true

        expect:
        !fluentBuilder.hasBuild(builderElement, targetType, ctx)
    }

    def 'hasBuild is false when build() does not produce the target'() {
        def build = noArgMethod('build', builderType)
        ctx.membersOf(builderElement) >> { Stream.of(build) }
        ctx.isMethod(build) >> true
        ctx.isPrivate(build) >> false
        ctx.erasure(builderType) >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(builderType, targetType) >> false

        expect:
        !fluentBuilder.hasBuild(builderElement, targetType, ctx)
    }

    def 'offer yields nothing when the builder has no build() producing the target, though its setters match'() {
        ProduceDemand demand = Stub()
        demand.nullnessOf(_ as TypeMirror, _ as Element) >> Nullability.NON_NULL
        def entry = noArgMethod('builder', builderType)
        def build = noArgMethod('build', builderType)
        def nameSetter = singleArgMethod('name', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.membersOf(builderElement) >> { Stream.of(build, nameSetter) }
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        ctx.isStatic(entry) >> true
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(builderType, targetType) >> false
        ctx.isAssignable(builderType, builderType) >> true

        expect:
        fluentBuilder.offer(targetType, targetElement, ['name'], demand, ctx).empty
    }

    // ---- member matching helpers, each exercised directly -----------------------------------------------------

    def 'methodNamed yields nothing for a member that is not a method'() {
        Element member = Stub()
        ctx.isMethod(member) >> false

        expect:
        fluentBuilder.methodNamed(member, 'builder', ctx).empty
    }

    def 'methodNamed yields nothing for a private method'() {
        def method = noArgMethod('builder', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> true

        expect:
        fluentBuilder.methodNamed(method, 'builder', ctx).empty
    }

    def 'methodNamed yields nothing when the name differs'() {
        def method = noArgMethod('newBuilder', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.methodNamed(method, 'builder', ctx).empty
    }

    def 'methodNamed yields the method when it is a non-private match'() {
        def method = noArgMethod('builder', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.methodNamed(method, 'builder', ctx).get().is(method)
    }

    def 'noArgMethodNamed rejects a method that takes parameters'() {
        def method = singleArgMethod('builder', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.noArgMethodNamed(method, 'builder', ctx).empty
    }

    def 'noArgMethodNamed accepts a zero-parameter match'() {
        def method = noArgMethod('builder', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.noArgMethodNamed(method, 'builder', ctx).get().is(method)
    }

    def 'singleArgMethodNamed rejects a zero-parameter method'() {
        def method = noArgMethod('name', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.singleArgMethodNamed(method, 'name', ctx).empty
    }

    def 'singleArgMethodNamed accepts a one-parameter match'() {
        def method = singleArgMethod('name', builderType)
        ctx.isMethod(method) >> true
        ctx.isPrivate(method) >> false

        expect:
        fluentBuilder.singleArgMethodNamed(method, 'name', ctx).get().is(method)
    }

    // ---- the fluent convention's own axes ---------------------------------------------------------------------

    def 'the fluent convention names the setter after the child itself'() {
        expect:
        fluentBuilder.setterName('name') == 'name'
    }

    def 'labelHead reads as the target followed by its factory'() {
        targetElement.simpleName >> nameOf('Person')

        expect:
        fluentBuilder.labelHead(targetElement, builderElement) == 'Person.builder'
    }

    def 'entryCall opens the chain with the static factory on the target'() {
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Person')
        targetElement.simpleName >> nameOf('Person')
        targetElement.enclosingElement >> enclosing

        expect:
        fluentBuilder.entryCall(targetElement, builderElement).toString() == 'com.example.Person.builder()'
    }

    // ---- weight: priced inverse to ConstructorCall ------------------------------------------------------------

    def 'weight is EXPENSIVE when the preference is absent, so the constructor is preferred by default'() {
        when:
        def weight = fluentBuilder.weight(ctx)

        then:
        1 * ctx.option('percolate.construction.preference') >> Optional.empty()
        0 * _

        expect:
        weight == Weights.EXPENSIVE
    }

    def 'weight is STEP when the preference names the builder'() {
        when:
        def weight = fluentBuilder.weight(ctx)

        then:
        1 * ctx.option('percolate.construction.preference') >> Optional.of('builder')
        0 * _

        expect:
        weight == Weights.STEP
    }

    // ---- port typing ------------------------------------------------------------------------------------------

    def 'port is named after the declared child and typed from the setter parameter'() {
        TypeMirror paramType = Stub()
        VariableElement param = Stub()
        ProduceDemand demand = Mock()
        ExecutableElement setter = Stub()
        setter.parameters >> [param]
        setter.simpleName >> nameOf('setName')
        param.asType() >> paramType

        when:
        def port = fluentBuilder.port('name', setter, demand)

        then:
        1 * demand.nullnessOf(paramType, param) >> Nullability.NULLABLE
        0 * _

        expect:
        port.nullness == Nullability.NULLABLE
        port.name == 'name'
        port.subTarget
    }

    def 'ports pairs each declared child with the setter matched for it, by position'() {
        ProduceDemand demand = Stub()
        demand.nullnessOf(_ as TypeMirror, _ as Element) >> Nullability.NON_NULL
        def nameSetter = singleArgMethod('setName', builderType)
        def ageSetter = singleArgMethod('setAge', builderType)

        expect:
        fluentBuilder.ports(['name', 'age'], [nameSetter, ageSetter], demand)*.name == ['name', 'age']
    }

    /** An {@link IncomingValues} resolving each port by its slot name, as an assembly operation does. */
    private static IncomingValues byName(final Map<String, CodeBlock> slots) {
        [byName: { String slot -> slots[slot] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    /** Stubs the seam end to end: a Person with a static builder() carrying build() and one setter per child. */
    private void stubWholeBuilder(final List<String> children) {
        def entry = noArgMethod('builder', builderType)
        def build = noArgMethod('build', targetType)
        def setters = children.collect { singleArgMethod(it, builderType) }
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Person')
        targetElement.simpleName >> nameOf('Person')
        targetElement.enclosingElement >> enclosing
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.membersOf(builderElement) >> { Stream.of(([build] + setters) as ExecutableElement[]) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        ctx.isStatic(entry) >> true
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(targetType, targetType) >> true
        ctx.isAssignable(builderType, builderType) >> true
    }

    /** Stubs a builder exposing exactly the named single-argument, self-returning setters. */
    private void stubSetters(final List<String> names) {
        def setters = names.collect { singleArgMethod(it, builderType) }
        ctx.membersOf(builderElement) >> { Stream.of(setters as ExecutableElement[]) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.isAssignable(builderType, builderType) >> true
    }

    private ExecutableElement noArgMethod(final String name, final TypeMirror returns) {
        ExecutableElement method = Mock()
        method.simpleName >> nameOf(name)
        method.parameters >> []
        method.returnType >> returns
        method
    }

    private ExecutableElement singleArgMethod(final String name, final TypeMirror returns) {
        ExecutableElement method = Mock()
        VariableElement param = Mock()
        TypeMirror paramType = Mock()
        paramType.toString() >> 'String'
        param.simpleName >> nameOf(name)
        param.asType() >> paramType
        method.simpleName >> nameOf(name)
        method.parameters >> [param]
        method.returnType >> returns
        method
    }
}
