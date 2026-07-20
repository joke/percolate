package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.javapoet.CodeBlock
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
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link GetterPathResolver} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): member reflection is stubbed on a mocked {@code ResolveCtx} over opaque
 * {@link ExecutableElement} member tokens. No javac, no {@code ResolveCtxBuilder}, no shape fixtures.
 */
@Tag('unit')
class GetterPathResolverSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror parentType = Mock()
    TypeElement parent = Mock()

    def 'matches a getX accessor as a unary operation typed to the return type'() {
        ExecutableElement getter = Mock()
        TypeMirror returnType = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(getter)
        ctx.isMethod(getter) >> true
        getter.parameters >> []
        getter.simpleName >> nameOf('getName')
        getter.returnType >> returnType

        when:
        def specs = new GetterPathResolver().descend(Demands.descend(parentType, 'name'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.STEP_GETTER
        spec.ports.size() == 1
        spec.ports[0].type.is(parentType)
        spec.outputType.is(returnType)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'types the produced value through the demand nullness oracle'() {
        ExecutableElement getter = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(getter)
        ctx.isMethod(getter) >> true
        getter.parameters >> []
        getter.simpleName >> nameOf('getName')
        getter.returnType >> Mock(TypeMirror)

        when:
        def specs = new GetterPathResolver()
                .descend(Demands.descend(parentType, 'name', Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputNullness == Nullability.NULLABLE
    }

    def 'matches an isX accessor for a boolean-returning method'() {
        ExecutableElement getter = Mock()
        TypeMirror returnType = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(getter)
        ctx.isMethod(getter) >> true
        getter.parameters >> []
        getter.simpleName >> nameOf('isFlag')
        getter.returnType >> returnType
        ctx.kind(returnType) >> TypeKind.BOOLEAN

        when:
        def specs = new GetterPathResolver().descend(Demands.descend(parentType, 'flag'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputType.is(returnType)
        specs[0].weight == Weights.STEP_GETTER
    }

    def 'rejects parameterized overloads when no zero-arg getter exists'() {
        ExecutableElement getter = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(getter)
        ctx.isMethod(getter) >> true
        getter.parameters >> [Mock(javax.lang.model.element.VariableElement)]
        getter.simpleName >> nameOf('getName')

        expect:
        new GetterPathResolver().descend(Demands.descend(parentType, 'name'), ctx).toList().empty
    }

    def 'ignores methods declared on java.lang.Object'() {
        ExecutableElement getter = Mock()
        TypeElement objectElement = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(getter)
        ctx.isMethod(getter) >> true
        getter.parameters >> []
        getter.enclosingElement >> objectElement
        objectElement.qualifiedName >> nameOf('java.lang.Object')

        expect:
        new GetterPathResolver().descend(Demands.descend(parentType, 'name'), ctx).toList().empty
    }

    def 'returns empty for non-declared parent types'() {
        ctx.asTypeElement(parentType) >> Optional.empty()

        expect:
        new GetterPathResolver().descend(Demands.descend(parentType, 'length'), ctx).toList().empty
    }

    def 'capitalize upper-cases the first character and leaves the rest, empty stays empty'() {
        expect:
        GetterPathResolver.capitalize('name') == 'Name'
        GetterPathResolver.capitalize('Name') == 'Name'
        GetterPathResolver.capitalize('') == ''
    }

    def 'matchGetter delegates to a zero-arg method named exactly getterName'() {
        ExecutableElement member = Mock()
        TypeElement enclosing = Mock()
        ctx.isMethod(member) >> true
        member.parameters >> []
        member.simpleName >> nameOf('getName')
        member.enclosingElement >> enclosing
        enclosing.qualifiedName >> nameOf('io.example.Person')

        expect:
        new GetterPathResolver().matchGetter(member, 'getName', ctx).get().is(member)
    }

    def 'matchBooleanIs rejects a no-arg isX method whose return type is not boolean'() {
        ExecutableElement member = Mock()
        TypeElement enclosing = Mock()
        TypeMirror returnType = Mock()
        ctx.isMethod(member) >> true
        member.parameters >> []
        member.simpleName >> nameOf('isName')
        member.enclosingElement >> enclosing
        enclosing.qualifiedName >> nameOf('io.example.Person')
        member.returnType >> returnType
        ctx.kind(returnType) >> TypeKind.INT
        ctx.qualifiedName(returnType) >> 'int'

        expect:
        new GetterPathResolver().matchBooleanIs(member, 'isName', ctx).empty
    }

    def 'matchBooleanIs matches a no-arg isX method returning primitive boolean'() {
        ExecutableElement member = Mock()
        TypeElement enclosing = Mock()
        TypeMirror returnType = Mock()
        ctx.isMethod(member) >> true
        member.parameters >> []
        member.simpleName >> nameOf('isFlag')
        member.enclosingElement >> enclosing
        enclosing.qualifiedName >> nameOf('io.example.Person')
        member.returnType >> returnType
        ctx.kind(returnType) >> TypeKind.BOOLEAN

        expect:
        new GetterPathResolver().matchBooleanIs(member, 'isFlag', ctx).get().is(member)
    }

    def 'isBooleanReturn is true for the java.lang.Boolean wrapper return type'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        method.returnType >> returnType
        ctx.kind(returnType) >> TypeKind.DECLARED
        ctx.qualifiedName(returnType) >> 'java.lang.Boolean'

        expect:
        new GetterPathResolver().isBooleanReturn(method, ctx)
    }

    def 'isBooleanReturn is false for a non-boolean declared return type'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        method.returnType >> returnType
        ctx.kind(returnType) >> TypeKind.DECLARED
        ctx.qualifiedName(returnType) >> 'java.lang.String'

        expect:
        !new GetterPathResolver().isBooleanReturn(method, ctx)
    }

    def 'step renders a zero-arg method call named after the method, weighted STEP_GETTER'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        method.returnType >> returnType
        method.simpleName >> nameOf('getName')

        expect:
        def step = GetterPathResolver.step(method)
        step.outputType.is(returnType)
        step.member.is(method)
        step.label == 'getName()'
        step.weight == Weights.STEP_GETTER
        CodeBlock.of('$L\n', step.codegen.render(singleInput(CodeBlock.of('$N', 'p')))).toString().contains('p.getName()')
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static io.github.joke.percolate.spi.IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as io.github.joke.percolate.spi.IncomingValues
    }
}
