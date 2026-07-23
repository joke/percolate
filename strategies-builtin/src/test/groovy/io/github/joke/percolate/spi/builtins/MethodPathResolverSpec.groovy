package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link MethodPathResolver} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): member reflection is stubbed on a mocked {@code ResolveCtx} over opaque
 * {@link ExecutableElement} member tokens. No javac, no {@code ResolveCtxBuilder}, no shape fixtures. It does not
 * gate behaviour on {@code ElementKind.RECORD}: a canonical record accessor and a non-record fluent accessor are
 * matched identically, as no-arg methods named after the segment.
 */
@Tag('unit')
class MethodPathResolverSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror parentType = Mock()
    TypeElement parent = Mock()

    def 'matches a canonical record accessor as a unary operation typed to the return type'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(method)
        ctx.isMethod(method) >> true
        method.parameters >> []
        method.simpleName >> nameOf('x')
        method.returnType >> returnType

        when:
        def specs = new MethodPathResolver().descend(Demands.descend(parentType, 'x'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.outputType.is(returnType)
        spec.outputNullness == Nullability.NON_NULL
        spec.weight == Weights.STEP_METHOD
        spec.ports.size() == 1
        spec.ports[0].type.is(parentType)
    }

    def 'matches a non-record fluent-style accessor'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(method)
        ctx.isMethod(method) >> true
        method.parameters >> []
        method.simpleName >> nameOf('street')
        method.returnType >> returnType

        when:
        def specs = new MethodPathResolver().descend(Demands.descend(parentType, 'street'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputType.is(returnType)
        specs[0].weight == Weights.STEP_METHOD
    }

    def 'rejects parameterised methods of the same name'() {
        ExecutableElement method = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(method)
        ctx.isMethod(method) >> true
        method.parameters >> [Mock(javax.lang.model.element.VariableElement)]
        method.simpleName >> nameOf('getName')

        expect:
        new MethodPathResolver().descend(Demands.descend(parentType, 'getName'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        ExecutableElement method = Mock()
        TypeElement objectElement = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(method)
        ctx.isMethod(method) >> true
        method.parameters >> []
        method.enclosingElement >> objectElement
        objectElement.qualifiedName >> nameOf('java.lang.Object')

        expect:
        new MethodPathResolver().descend(Demands.descend(parentType, 'toString'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        ctx.asTypeElement(parentType) >> Optional.empty()

        expect:
        new MethodPathResolver().descend(Demands.descend(parentType, 'length'), ctx).toList().empty
    }

    def 'matchAccessor rejects a method whose name does not match the segment'() {
        ExecutableElement method = Mock()
        TypeElement enclosing = Mock()
        ctx.isMethod(method) >> true
        method.parameters >> []
        method.enclosingElement >> enclosing
        enclosing.qualifiedName >> nameOf('io.example.Person')
        method.simpleName >> nameOf('other')

        expect:
        new MethodPathResolver().matchAccessor(method, 'street', ctx).empty
    }

    def 'matchAccessor matches a no-arg method whose name equals the segment exactly'() {
        ExecutableElement method = Mock()
        TypeElement enclosing = Mock()
        ctx.isMethod(method) >> true
        method.parameters >> []
        method.enclosingElement >> enclosing
        enclosing.qualifiedName >> nameOf('io.example.Person')
        method.simpleName >> nameOf('street')

        expect:
        new MethodPathResolver().matchAccessor(method, 'street', ctx).get().is(method)
    }

    def 'step renders a zero-arg method call named after the segment and carries the STEP_METHOD weight'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        method.returnType >> returnType

        expect:
        def step = MethodPathResolver.step(method, 'street')
        step.outputType.is(returnType)
        step.member.is(method)
        step.label == 'street()'
        step.weight == Weights.STEP_METHOD
        io.github.joke.percolate.lib.javapoet.CodeBlock.of('$L\n', step.codegen.render(singleInput(io.github.joke.percolate.lib.javapoet.CodeBlock.of('$N', 'p')))).toString().contains('p.street()')
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static io.github.joke.percolate.spi.IncomingValues singleInput(final io.github.joke.percolate.lib.javapoet.CodeBlock value) {
        [single: { -> value }] as io.github.joke.percolate.spi.IncomingValues
    }
}
