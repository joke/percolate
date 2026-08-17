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
 * {@link SideLocatedBuilder} — {@code new MyClassBuilder().name(v).build()} — unit-tested mock-only over the
 * {@link ResolveCtx} seam. Its discovery is the one that cannot be found by sniffing the target's own members, so
 * the name is only where the search starts: the match is decided structurally, and a same-named type that is
 * private or has no accessible no-arg constructor does not match.
 */
@Tag('unit')
class SideLocatedBuilderSpec extends Specification {

    ResolveCtx ctx = Mock()
    SideLocatedBuilder sideLocatedBuilder = new SideLocatedBuilder()
    TypeMirror targetType = Mock()
    TypeElement targetElement = Mock()
    TypeElement builderElement = Mock()
    TypeMirror builderType = Mock()

    def 'builderName suffixes the target qualified name'() {
        targetElement.qualifiedName >> nameOf('com.example.MyClass')

        expect:
        sideLocatedBuilder.builderName(targetElement) == 'com.example.MyClassBuilder'
    }

    def 'builderFor finds the sibling builder type by name'() {
        def ctor = noArgConstructor()
        targetElement.qualifiedName >> nameOf('com.example.MyClass')
        ctx.typeElementNamed('com.example.MyClassBuilder') >> builderElement
        ctx.isPrivate(builderElement) >> false
        ctx.membersOf(builderElement) >> { Stream.of(ctor) }
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false

        expect:
        sideLocatedBuilder.builderFor(targetElement, ctx).get().is(builderElement)
    }

    def 'builderFor yields nothing when no sibling type carries the name'() {
        targetElement.qualifiedName >> nameOf('com.example.MyClass')
        ctx.typeElementNamed('com.example.MyClassBuilder') >> null

        expect:
        sideLocatedBuilder.builderFor(targetElement, ctx).empty
    }

    def 'builderFor yields nothing when the sibling type is private'() {
        targetElement.qualifiedName >> nameOf('com.example.MyClass')
        ctx.typeElementNamed('com.example.MyClassBuilder') >> builderElement
        ctx.isPrivate(builderElement) >> true

        expect:
        sideLocatedBuilder.builderFor(targetElement, ctx).empty
    }

    def 'builderFor yields nothing when the sibling type has no accessible no-arg constructor'() {
        def ctor = noArgConstructor()
        targetElement.qualifiedName >> nameOf('com.example.MyClass')
        ctx.typeElementNamed('com.example.MyClassBuilder') >> builderElement
        ctx.isPrivate(builderElement) >> false
        ctx.membersOf(builderElement) >> { Stream.of(ctor) }
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> true

        expect:
        sideLocatedBuilder.builderFor(targetElement, ctx).empty
    }

    def 'isNoArgConstructor rejects a member that is not a constructor'() {
        def method = noArgConstructor()
        ctx.isConstructor(method) >> false

        expect:
        !sideLocatedBuilder.isNoArgConstructor(method, ctx)
    }

    def 'isNoArgConstructor rejects a constructor that takes parameters'() {
        ExecutableElement ctor = Mock()
        VariableElement param = Mock()
        ctor.parameters >> [param]
        ctx.isConstructor(ctor) >> true
        ctx.isPrivate(ctor) >> false

        expect:
        !sideLocatedBuilder.isNoArgConstructor(ctor, ctx)
    }

    def 'labelHead reads as a construction of the builder'() {
        builderElement.simpleName >> nameOf('MyClassBuilder')

        expect:
        sideLocatedBuilder.labelHead(targetElement, builderElement) == 'new MyClassBuilder'
    }

    def 'entryCall opens the chain by constructing the builder, not by a factory on the target'() {
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'MyClassBuilder')
        builderElement.simpleName >> nameOf('MyClassBuilder')
        builderElement.enclosingElement >> enclosing

        expect:
        sideLocatedBuilder.entryCall(targetElement, builderElement).toString() == 'new com.example.MyClassBuilder()'
    }

    def 'renders a construction-opened chain'() {
        def ctor = noArgConstructor()
        def build = noArgMethod('build', targetType)
        def nameSetter = singleArgMethod('name', builderType)
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'MyClassBuilder')
        targetElement.qualifiedName >> nameOf('com.example.MyClass')
        builderElement.simpleName >> nameOf('MyClassBuilder')
        builderElement.enclosingElement >> enclosing
        ctx.asTypeElement(targetType) >> Optional.of(targetElement)
        ctx.typeElementNamed('com.example.MyClassBuilder') >> builderElement
        ctx.membersOf(builderElement) >> { Stream.of(ctor, build, nameSetter) }
        ctx.isConstructor(ctor) >> true
        ctx.isMethod(build) >> true
        ctx.isMethod(nameSetter) >> true
        ctx.isPrivate(_ as Element) >> false
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.erasure(targetType) >> targetType
        ctx.isAssignable(targetType, targetType) >> true
        ctx.isAssignable(builderType, builderType) >> true
        ctx.option('percolate.construction.preference') >> Optional.empty()

        def spec = sideLocatedBuilder.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec.first()
        def rendered = CodeBlock.of('$L\n', spec.codegen.render(byName([name: CodeBlock.of('$N', 'n')]))).toString()

        expect:
        spec.ports*.name == ['name']
        spec.label == 'new MyClassBuilder(String).build()'
        rendered.contains('new com.example.MyClassBuilder()')
        rendered.contains('.name(n)')
        rendered.contains('.build()')
    }

    private static IncomingValues byName(final Map<String, CodeBlock> slots) {
        [byName: { String slot -> slots[slot] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private ExecutableElement noArgConstructor() {
        ExecutableElement ctor = Mock()
        ctor.parameters >> []
        ctor
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
