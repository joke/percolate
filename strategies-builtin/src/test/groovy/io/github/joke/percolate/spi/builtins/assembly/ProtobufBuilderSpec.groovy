package io.github.joke.percolate.spi.builtins.assembly

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
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
 * {@link ProtobufBuilder} — {@code Person.newBuilder().setName(v).build()} — unit-tested mock-only over the
 * {@link ResolveCtx} seam. It varies both naming axes from {@link FluentBuilder}, which is what proves
 * {@link BuilderAssembly} is not secretly shaped around one convention.
 */
@Tag('unit')
class ProtobufBuilderSpec extends Specification {

    ResolveCtx ctx = Mock()
    ProtobufBuilder protobufBuilder = new ProtobufBuilder()
    TypeMirror targetType = Mock()
    TypeElement targetElement = Mock()
    TypeElement builderElement = Mock()
    TypeMirror builderType = Mock()

    def 'the protobuf convention prefixes the child with set'() {
        expect:
        protobufBuilder.setterName('name') == 'setName'
        protobufBuilder.setterName('emailAddress') == 'setEmailAddress'
    }

    def 'prefixed capitalises the child and appends it to the prefix'() {
        expect:
        protobufBuilder.prefixed(prefix, child) == expected

        where:
        prefix | child          | expected
        'set'  | 'name'         | 'setName'
        'with' | 'name'         | 'withName'
        'set'  | 'emailAddress' | 'setEmailAddress'
        'set'  | 'a'            | 'setA'
        'set'  | 'URL'          | 'setURL'
    }

    def 'prefixed leaves the prefix alone for an empty child'() {
        expect:
        protobufBuilder.prefixed('set', '') == 'set'
    }

    def 'labelHead reads as the target followed by newBuilder'() {
        targetElement.simpleName >> nameOf('Person')

        expect:
        protobufBuilder.labelHead(targetElement, builderElement) == 'Person.newBuilder'
    }

    def 'entryCall opens the chain with the static newBuilder factory'() {
        Element enclosing = Stub()
        enclosing.accept({ it instanceof ElementVisitor }, null) >> ClassName.get('com.example', 'Person')
        targetElement.simpleName >> nameOf('Person')
        targetElement.enclosingElement >> enclosing

        expect:
        protobufBuilder.entryCall(targetElement, builderElement).toString() == 'com.example.Person.newBuilder()'
    }

    def 'builderFor finds the type the static newBuilder factory returns'() {
        def entry = noArgMethod('newBuilder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> false
        ctx.isStatic(entry) >> true
        ctx.asTypeElement(builderType) >> Optional.of(builderElement)

        expect:
        protobufBuilder.builderFor(targetElement, ctx).get().is(builderElement)
    }

    def 'builderFor ignores a fluent-style builder factory'() {
        def entry = noArgMethod('builder', builderType)
        ctx.membersOf(targetElement) >> { Stream.of(entry) }
        ctx.isMethod(entry) >> true
        ctx.isPrivate(entry) >> false
        ctx.isStatic(entry) >> true

        expect:
        protobufBuilder.builderFor(targetElement, ctx).empty
    }

    def 'setter resolves the declared child through its set-prefixed builder method'() {
        def setName = singleArgMethod('setName', builderType)
        def plainName = singleArgMethod('name', builderType)
        ctx.membersOf(builderElement) >> { Stream.of(plainName, setName) }
        ctx.isMethod(_ as Element) >> true
        ctx.isPrivate(_ as Element) >> false
        builderElement.asType() >> builderType
        ctx.erasure(builderType) >> builderType
        ctx.isAssignable(builderType, builderType) >> true

        expect:
        protobufBuilder.setter(builderElement, 'name', ctx).get().is(setName)
    }

    def 'emits one operation whose ports carry the declared child names, not the setter names'() {
        stubWholeBuilder(['name', 'age'])
        ctx.option('percolate.construction.preference') >> Optional.of('builder')

        def specs = protobufBuilder.expand(Demands.assembling(targetType, ['name', 'age'] as Set), ctx)*.spec

        expect:
        specs.size() == 1
        verifyAll(specs[0]) {
            ports*.name == ['name', 'age']
            weight == Weights.STEP
            label == 'Person.newBuilder(String, String).build()'
        }
    }

    def 'renders the set-prefixed chain'() {
        stubWholeBuilder(['name'])
        ctx.option('percolate.construction.preference') >> Optional.empty()

        def spec = protobufBuilder.expand(Demands.assembling(targetType, ['name'] as Set), ctx)*.spec.first()
        def rendered = CodeBlock.of('$L\n', spec.codegen.render(byName([name: CodeBlock.of('$N', 'n')]))).toString()

        expect:
        rendered.contains('com.example.Person.newBuilder()')
        rendered.contains('.setName(n)')
        rendered.contains('.build()')
    }

    private static IncomingValues byName(final Map<String, CodeBlock> slots) {
        [byName: { String slot -> slots[slot] }] as IncomingValues
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private void stubWholeBuilder(final List<String> children) {
        def entry = noArgMethod('newBuilder', builderType)
        def build = noArgMethod('build', targetType)
        def setters = children.collect { singleArgMethod(protobufBuilder.setterName(it), builderType) }
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
