package io.github.joke.percolate.spi.builtins.value

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Offer
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Subjects
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link NullnessCrossing} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): the guard/coalesce over-emission is driven entirely by stubbed seam
 * questions over opaque {@link TypeMirror} tokens; {@link io.github.joke.percolate.spi.LiteralCoercion} (a
 * production {@code spi} class, unchanged) still reads the target's raw {@code getKind()}/{@code asElement()}, which
 * are stubbed directly since they are not {@code ResolveCtx} seam questions. No javac.
 */
@Tag('unit')
class NullnessCrossingSpec extends Specification {

    ResolveCtx ctx = Mock()
    NullnessCrossing crossing = new NullnessCrossing()

    // Below every other strategy's default, so a crossing is applied before anything else competes for the slot.
    def 'priority outcompetes the default strategy priority'() {
        expect:
        crossing.priority() == -1
    }

    def 'emits a partial requireNonNull for a non-null reference-scalar demand'() {
        DeclaredType stringType = Mock()
        ctx.isDeclared(stringType) >> true

        when:
        def specs = crossing.expand(Demands.crossing(stringType, 'name'), ctx)*.spec

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports[0].type.is(stringType)
        spec.ports[0].nullness == Nullability.NULLABLE
        spec.ports[0].selector == Port.Selector.BY_TYPE && spec.ports[0].onMiss == Port.OnMiss.DECLINE
        spec.outputType.is(stringType)
        spec.outputNullness == Nullability.NON_NULL

        and: 'the rendered guard names the slot in its message (design D7 repatriation of NullnessCrossingEndToEndSpec)'
        def rendered = spec.codegen.render(singleInput(CodeBlock.of('$N', 'src'))).toString()
        rendered.contains('requireNonNull')
        rendered.contains("source for slot 'name' is null but target is non-null")
    }

    def 'a nullable demand needs no crossing'() {
        DeclaredType stringType = Mock()

        expect:
        crossing
                .expand(Demands.forTarget(stringType, Nullability.NULLABLE), ctx)
                .toList()
                .empty
    }

    def 'a default over-emits a total scalar coalesce and the Optional coalesce alongside the partial requireNonNull'() {
        DeclaredType stringType = Mock()
        TypeElement stringElement = Mock()
        TypeElement optionalElement = Mock()
        TypeMirror optionalOfString = Mock()
        stringType.kind >> TypeKind.DECLARED
        stringType.asElement() >> stringElement
        stringElement.qualifiedName >> nameOf('java.lang.String')
        ctx.isDeclared(stringType) >> true
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        when:
        def specs = crossing.expand(Demands.crossing(stringType, 'name', 'unknown'), ctx)*.spec

        then: 'a total coalesce over a NULLABLE scalar port'
        def scalar = specs.find { !it.partial && it.ports[0].type.is(stringType) }
        scalar != null
        scalar.weight == Weights.NOOP
        scalar.childScope.empty
        scalar.ports[0].nullness == Nullability.NULLABLE
        scalar.outputType.is(stringType)
        scalar.outputNullness == Nullability.NON_NULL

        and: 'a total coalesce over a present Optional<String> port'
        def optional = specs.find { !it.partial && it.ports[0].type.is(optionalOfString) }
        optional != null
        optional.ports[0].nullness == Nullability.NON_NULL
        optional.outputType.is(stringType)

        and: 'the partial requireNonNull is also offered (totality picks coalesce in extraction)'
        specs.any { it.partial }

        and: 'every crossing port is REUSE — the driver binds an in-scope source or the op does not apply'
        specs.every { it.ports[0].selector == Port.Selector.BY_TYPE && it.ports[0].onMiss == Port.OnMiss.DECLINE }

        and: 'the guard names the demand\'s binding slot, and both coalesces consume the declared default'
        specs.find { it.partial }.codegen.render(singleInput(CodeBlock.of('$N', 'src'))).toString()
                == 'java.util.Objects.requireNonNull(src, "source for slot \'name\' is null but target is non-null")'
        scalar.consumed == [DirectiveInput.scalar('defaultValue', 'unknown', Subjects.none())] as Set
        optional.consumed == scalar.consumed

        and: 'both coalesces render the coerced literal itself as the fallback operand'
        scalar.codegen.render(singleInput(CodeBlock.of('$N', 'src'))).toString()
                == 'java.util.Objects.requireNonNullElse(src, "unknown")'
        CodeBlock.of('$L\n', optional.codegen.render(singleInput(CodeBlock.of('$N', 'src')))).toString()
                == 'src.orElse("unknown")\n'
    }

    // A nullable target still uses its declared fallback; only the requireNonNull guard is tied to NON_NULL.
    def 'a nullable demand with a declared default coalesces without any guard'() {
        DeclaredType stringType = Mock()
        TypeElement stringElement = Mock()
        TypeElement optionalElement = Mock()
        TypeMirror optionalOfString = Mock()
        stringType.kind >> TypeKind.DECLARED
        stringType.asElement() >> stringElement
        stringElement.qualifiedName >> nameOf('java.lang.String')
        ctx.isDeclared(stringType) >> true
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        when:
        def specs = crossing.expand(
                Demands.crossing(stringType, 'name', 'unknown', Nullability.NULLABLE), ctx)*.spec

        then:
        specs*.label == ['coalesce', 'coalesce']
        specs.every { !it.partial }
    }

    def 'coerces the default literal to a wrapper target type'() {
        DeclaredType integerType = Mock()
        TypeElement integerElement = Mock()
        TypeElement optionalElement = Mock()
        integerType.kind >> TypeKind.DECLARED
        integerType.asElement() >> integerElement
        integerElement.qualifiedName >> nameOf('java.lang.Integer')
        ctx.isDeclared(integerType) >> true
        ctx.isReferenceType(integerType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, integerType) >> Mock(TypeMirror)

        when:
        def specs = crossing.expand(Demands.crossing(integerType, 'n', '0'), ctx)*.spec

        then:
        def scalar = specs.find { !it.partial && it.ports[0].type.is(integerType) }
        scalar != null
        scalar.outputType.is(integerType)
        scalar.ports[0].nullness == Nullability.NULLABLE
    }

    def 'emits nothing for a primitive target (a primitive can never be absent)'() {
        TypeMirror intType = Mock()
        intType.kind >> TypeKind.INT
        ctx.isDeclared(intType) >> false
        ctx.isReferenceType(intType) >> false

        expect:
        crossing.expand(Demands.crossing(intType, 'n', '0'), ctx).toList().empty
    }

    def 'an uncoercible default refuses the whole demand, dropping the partial guard'() {
        DeclaredType integerType = Mock()
        TypeElement integerElement = Mock()
        integerType.kind >> TypeKind.DECLARED
        integerType.asElement() >> integerElement
        integerElement.qualifiedName >> nameOf('java.lang.Integer')
        integerElement.simpleName >> nameOf('Integer')
        ctx.isDeclared(integerType) >> true
        ctx.simpleName(integerType) >> 'Integer'

        when:
        def offers = crossing.expand(Demands.crossing(integerType, 'n', 'abc'), ctx).toList()

        then: 'a single refusal is emitted — no partial requireNonNull guard survives (design D1)'
        offers.size() == 1
        def refusal = offers[0]
        refusal instanceof Offer.Refusal
        refusal.subject.is(Subjects.none())
        refusal.message == "cannot coerce 'abc' to Integer"
    }

    def 'guardOnly stays silent when the target is not NON_NULL'() {
        DeclaredType stringType = Mock()
        // Declared, so the only thing standing between this call and a guard is the nullness check itself.
        ctx.isDeclared(stringType) >> true

        expect:
        crossing.guardOnly(stringType, 'name', false, ctx).toList().empty
    }

    def 'guardOnly offers the bare requireNonNull guard when the target is NON_NULL'() {
        DeclaredType stringType = Mock()
        ctx.isDeclared(stringType) >> true

        expect:
        def offers = crossing.guardOnly(stringType, 'name', true, ctx).toList()
        offers.size() == 1
        offers[0].spec.partial
        offers[0].spec.label == 'requireNonNull'
    }

    def 'coalesce over-emits both the scalar and the Optional form'() {
        DeclaredType stringType = Mock()
        TypeElement optionalElement = Mock()
        TypeMirror optionalOfString = Mock()
        ctx.isDeclared(stringType) >> true
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        expect:
        def specs = crossing.coalesce(stringType, CodeBlock.of('$S', 'fallback'), defaultInput(), ctx).toList()
        specs.size() == 2
        specs*.label == ['coalesce', 'coalesce']
        specs[0].ports[0].type.is(stringType)
        specs[1].ports[0].type.is(optionalOfString)

        and: 'the scalar form coalesces through Objects, the Optional form through orElse'
        specs[0].codegen.render(singleInput(CodeBlock.of('$N', 'src'))).toString()
                == 'java.util.Objects.requireNonNullElse(src, "fallback")'
        CodeBlock.of('$L\n', specs[1].codegen.render(singleInput(CodeBlock.of('$N', 'box')))).toString()
                == 'box.orElse("fallback")\n'
    }

    def 'coalesce emits nothing for a target that is neither declared nor a reference'() {
        TypeMirror intType = Mock()
        ctx.isDeclared(intType) >> false
        ctx.isReferenceType(intType) >> false

        expect:
        crossing.coalesce(intType, CodeBlock.of('$L', 0), defaultInput(), ctx).toList().empty
    }

    def 'requireNonNullGuard is empty when the target is not NON_NULL-guarded'() {
        DeclaredType stringType = Mock()
        ctx.isDeclared(stringType) >> true

        expect:
        crossing.requireNonNullGuard(stringType, 'name', false, ctx).toList().empty
    }

    def 'requireNonNullGuard is empty when the target is not declared even if guarded'() {
        TypeMirror primitiveType = Mock()
        ctx.isDeclared(primitiveType) >> false

        expect:
        crossing.requireNonNullGuard(primitiveType, 'name', true, ctx).toList().empty
    }

    def 'requireNonNullGuard emits one requireNonNull spec when guarded and declared'() {
        DeclaredType stringType = Mock()
        ctx.isDeclared(stringType) >> true

        expect:
        crossing.requireNonNullGuard(stringType, 'name', true, ctx).toList().size() == 1
    }

    def 'requireNonNull builds a partial NOOP spec whose message names the slot'() {
        DeclaredType stringType = Mock()

        expect:
        def spec = crossing.requireNonNull(stringType, 'name')
        spec.partial
        spec.weight == Weights.NOOP
        spec.outputType.is(stringType)
        spec.outputNullness == Nullability.NON_NULL
        spec.codegen.render(singleInput(CodeBlock.of('$N', 'src'))).toString()
                == 'java.util.Objects.requireNonNull(src, "source for slot \'name\' is null but target is non-null")'
    }

    def 'coalesceSpec builds a total NOOP spec reusing the from-type at the given nullness'() {
        DeclaredType stringType = Mock()
        OperationCodegen codegen = { inputs -> CodeBlock.of('x') }

        expect:
        def spec = crossing.coalesceSpec(stringType, Nullability.NULLABLE, stringType, codegen,
                DirectiveInput.scalar('defaultValue', 'fallback', Subjects.none()))
        !spec.partial
        spec.label == 'coalesce'
        spec.weight == Weights.NOOP
        spec.ports[0].nullness == Nullability.NULLABLE
        spec.ports[0].selector == Port.Selector.BY_TYPE && spec.ports[0].onMiss == Port.OnMiss.DECLINE
        spec.outputNullness == Nullability.NON_NULL
        spec.consumed == [DirectiveInput.scalar('defaultValue', 'fallback', Subjects.none())] as Set
    }

    def 'optionalOf is empty for a non-reference element'() {
        TypeMirror primitiveType = Mock()
        TypeElement optionalElement = Mock()
        TypeMirror optionalOfPrimitive = Mock()
        ctx.isReferenceType(primitiveType) >> false
        // Optional itself resolves, so only the reference-type check can keep the result empty.
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, primitiveType) >> optionalOfPrimitive

        expect:
        crossing.optionalOf(primitiveType, ctx).empty
    }

    def 'optionalOf is empty when Optional itself is not resolvable'() {
        DeclaredType stringType = Mock()
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> null

        expect:
        crossing.optionalOf(stringType, ctx).empty
    }

    def 'optionalOf wraps a reference element in Optional when resolvable'() {
        DeclaredType stringType = Mock()
        TypeElement optionalElement = Mock()
        TypeMirror optionalOfString = Mock()
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        expect:
        crossing.optionalOf(stringType, ctx).get().is(optionalOfString)
    }

    private static DirectiveInput defaultInput() {
        DirectiveInput.scalar('defaultValue', 'fallback', Subjects.none())
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
