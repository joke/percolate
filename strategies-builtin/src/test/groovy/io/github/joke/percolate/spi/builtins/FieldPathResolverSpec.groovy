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
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link FieldPathResolver} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): member reflection is stubbed on a mocked {@code ResolveCtx} over opaque
 * {@link VariableElement} member tokens. No javac, no {@code ResolveCtxBuilder}, no shape fixtures.
 */
@Tag('unit')
class FieldPathResolverSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror parentType = Mock()
    TypeElement parent = Mock()

    def 'matches a public, non-static field as a unary accessor operation typed to the field type'() {
        VariableElement field = Mock()
        TypeMirror fieldType = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(field)
        ctx.isField(field) >> true
        field.simpleName >> nameOf('value')
        field.asType() >> fieldType

        when:
        def specs = new FieldPathResolver().descend(Demands.descend(parentType, 'value'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.STEP_FIELD
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        spec.ports[0].type.is(parentType)
        spec.outputType.is(fieldType)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'types the produced value through the demand nullness oracle'() {
        VariableElement field = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(field)
        ctx.isField(field) >> true
        field.simpleName >> nameOf('value')
        field.asType() >> Mock(TypeMirror)

        when:
        def specs = new FieldPathResolver()
                .descend(Demands.descend(parentType, 'value', Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputNullness == Nullability.NULLABLE
    }

    def 'rejects private fields'() {
        VariableElement field = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(field)
        ctx.isField(field) >> true
        field.simpleName >> nameOf('secret')
        ctx.isPrivate(field) >> true

        expect:
        new FieldPathResolver().descend(Demands.descend(parentType, 'secret'), ctx).toList().empty
    }

    def 'rejects static fields'() {
        VariableElement field = Mock()
        ctx.asTypeElement(parentType) >> Optional.of(parent)
        ctx.membersOf(parent) >> Stream.of(field)
        ctx.isField(field) >> true
        field.simpleName >> nameOf('DEFAULT')
        ctx.isStatic(field) >> true

        expect:
        new FieldPathResolver().descend(Demands.descend(parentType, 'DEFAULT'), ctx).toList().empty
    }

    def 'isVisibleField is true only for a non-private, non-static field'() {
        VariableElement field = Mock()
        ctx.isField(field) >> true
        ctx.isPrivate(field) >> false
        ctx.isStatic(field) >> false

        expect:
        new FieldPathResolver().isVisibleField(field, ctx)
    }

    def 'isVisibleField is false when the member is not a field at all'() {
        VariableElement field = Mock()
        ctx.isField(field) >> false

        expect:
        !new FieldPathResolver().isVisibleField(field, ctx)
    }

    def 'matchField rejects a visible field whose name does not match the segment'() {
        VariableElement field = Mock()
        ctx.isField(field) >> true
        ctx.isPrivate(field) >> false
        ctx.isStatic(field) >> false
        field.simpleName >> nameOf('other')

        expect:
        new FieldPathResolver().matchField(field, 'value', ctx).empty
    }

    def 'matchField matches a visible field whose name matches the segment exactly'() {
        VariableElement field = Mock()
        ctx.isField(field) >> true
        ctx.isPrivate(field) >> false
        ctx.isStatic(field) >> false
        field.simpleName >> nameOf('value')

        expect:
        new FieldPathResolver().matchField(field, 'value', ctx).get().is(field)
    }

    def 'step renders a plain field access and carries the STEP_FIELD weight'() {
        VariableElement field = Mock()
        TypeMirror fieldType = Mock()
        field.asType() >> fieldType

        expect:
        def step = FieldPathResolver.step(field, 'value')
        step.outputType.is(fieldType)
        step.member.is(field)
        step.label == '.value'
        step.weight == Weights.STEP_FIELD
        io.github.joke.percolate.lib.javapoet.CodeBlock.of('$L\n', step.codegen.render(singleInput(io.github.joke.percolate.lib.javapoet.CodeBlock.of('$N', 'p')))).toString().contains('p.value')
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static io.github.joke.percolate.spi.IncomingValues singleInput(final io.github.joke.percolate.lib.javapoet.CodeBlock value) {
        [single: { -> value }] as io.github.joke.percolate.spi.IncomingValues
    }
}
