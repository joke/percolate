package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
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

    def 'emits a partial requireNonNull for a non-null reference-scalar demand'() {
        DeclaredType stringType = Mock()
        ctx.isDeclared(stringType) >> true

        when:
        def specs = new NullnessCrossing().expand(Demands.crossing(stringType, 'name'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.weight == Weights.NOOP
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports[0].type.is(stringType)
        spec.ports[0].nullness == Nullability.NULLABLE
        spec.ports[0].sourcing == Port.Sourcing.REUSE
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
        new NullnessCrossing()
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
        def specs = new NullnessCrossing().expand(Demands.crossing(stringType, 'name', 'unknown'), ctx).toList()

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
        specs.every { it.ports[0].sourcing == Port.Sourcing.REUSE }
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
        def specs = new NullnessCrossing().expand(Demands.crossing(integerType, 'n', '0'), ctx).toList()

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
        new NullnessCrossing().expand(Demands.crossing(intType, 'n', '0'), ctx).toList().empty
    }

    def 'an uncoercible default yields only the guard, no coalesce'() {
        DeclaredType integerType = Mock()
        TypeElement integerElement = Mock()
        integerType.kind >> TypeKind.DECLARED
        integerType.asElement() >> integerElement
        integerElement.qualifiedName >> nameOf('java.lang.Integer')
        ctx.isDeclared(integerType) >> true

        when:
        def specs = new NullnessCrossing().expand(Demands.crossing(integerType, 'n', 'abc'), ctx).toList()

        then: 'the requireNonNull guard remains (NON_NULL declared target) but no total coalesce is offered'
        specs.every { it.partial }
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
