package io.github.joke.percolate.spi.builtins.assembly

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
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
 * {@link WithBuilder} — {@code Person.builder().withName(v).build()} — unit-tested mock-only over the
 * {@link ResolveCtx} seam. It shares {@link FluentBuilder}'s entry point and varies only the setter naming, so the
 * two are disjoint by setter match rather than by factory name.
 */
@Tag('unit')
class WithBuilderSpec extends Specification {

    ResolveCtx ctx = Mock()
    WithBuilder withBuilder = new WithBuilder()
    TypeMirror targetType = Mock()
    TypeElement targetElement = Mock()
    TypeElement builderElement = Mock()
    TypeMirror builderType = Mock()

    def 'the with convention prefixes the child with with'() {
        expect:
        withBuilder.setterName('name') == 'withName'
        withBuilder.setterName('emailAddress') == 'withEmailAddress'
    }

    def 'labelHead reads as the target followed by builder'() {
        targetElement.simpleName >> nameOf('Person')

        expect:
        withBuilder.labelHead(targetElement, builderElement) == 'Person.builder'
    }

    def 'entryCall opens the chain with the static builder factory'() {
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Person')
        targetElement.simpleName >> nameOf('Person')
        targetElement.enclosingElement >> enclosing

        expect:
        withBuilder.entryCall(targetElement, builderElement).toString() == 'com.example.Person.builder()'
    }

    def 'builderFor finds the type the static builder factory returns'() {
        def entry = noArgMethod('builder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> false
        ctx.isStatic(entry) >> true
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)

        expect:
        withBuilder.builderFor(targetElement, ctx).get().is(builderElement)
    }

    def 'setter declines a plainly-named setter, leaving that builder to the fluent convention'() {
        def plainName = singleArgMethod('name', builderType)
        ctx.membersOf(builderElement) >> { Stream.of(plainName) }
        ctx.isMethod(plainName) >> true
        ctx.isPrivate(plainName) >> false

        expect:
        withBuilder.setter(builderElement, 'name', ctx).empty
    }

    def 'renders the with-prefixed chain'() {
        def entry = noArgMethod('builder', builderType)
        def build = noArgMethod('build', targetType)
        def withName = singleArgMethod('withName', builderType)
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Person')
        targetElement.simpleName >> nameOf('Person')
        targetElement.enclosingElement >> enclosing
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.membersOf(builderElement) >> { Stream.of(build, withName) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        ctx.isStatic(entry) >> true
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(targetType, targetType) >> true
        ctx.isAssignable(builderType, builderType) >> true
        ctx.option('percolate.construction.preference') >> Optional.empty()

        def spec = withBuilder.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec.first()
        def rendered = CodeBlock.of('$L\n', spec.codegen.render(byName([name: CodeBlock.of('$N', 'n')]))).toString()

        expect:
        spec.ports*.name == ['name']
        rendered.contains('com.example.Person.builder()')
        rendered.contains('.withName(n)')
        rendered.contains('.build()')
    }

    private static IncomingValues byName(final Map<String, CodeBlock> slots) {
        [byName: { String slot -> slots[slot] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
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
