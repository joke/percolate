package io.github.joke.percolate.reactor

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.reactor.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SingleOptional} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac. Strict interaction
 * counts (each ending {@code 0 * _}) pin the guard chain precisely.
 */
@Tag('unit')
class SingleOptionalSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror to = Mock()
    TypeMirror inner = Mock()
    TypeMirror elementType = Mock()
    TypeElement monoElement = Mock()
    TypeMirror monoOfElement = Mock()

    def 'declines a target that is not a Mono'() {
        when:
        def specs = new SingleOptional().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines a Mono target whose inner type is not an Optional'() {
        when:
        def specs = new SingleOptional().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isOptional(inner) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines when reactor-core Mono is not on the compile classpath'() {
        when:
        def specs = new SingleOptional().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isOptional(inner) >> true
        1 * ctx.typeArgument(inner, 0) >> elementType
        1 * ctx.typeElementNamed(Reactors.MONO) >> null
        0 * _

        expect:
        specs.empty
    }

    def 'emits a singleOptional operation surfacing emptiness of a concrete Mono<T> as Mono<Optional<T>>'() {
        when:
        def specs = new SingleOptional().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isOptional(inner) >> true
        1 * ctx.typeArgument(inner, 0) >> elementType
        1 * ctx.typeElementNamed(Reactors.MONO) >> monoElement
        1 * ctx.declaredType(monoElement, elementType) >> monoOfElement
        0 * _

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'singleOptional'
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.STEP
        spec.childScope.empty
        spec.ports.size() == 1
        spec.ports[0].name == 'mono'
        spec.ports[0].type.is(monoOfElement)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(to)
        spec.outputNullness == Nullability.NON_NULL
        CodeBlock.of('$L\n', ((OperationCodegen) spec.codegen).render(singleInput(CodeBlock.of('$N', 'source'))))
                .toString() == 'source.singleOptional()\n'
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
