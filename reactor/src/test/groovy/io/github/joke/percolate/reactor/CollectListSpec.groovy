package io.github.joke.percolate.reactor

import io.github.joke.percolate.lib.javapoet.CodeBlock
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
 * {@link CollectList} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac. Strict interaction
 * counts (each ending {@code 0 * _}) pin the guard chain precisely, since a loose stub-only style leaves many guard
 * mutants (negated conditionals, removed seam calls, substituted arguments) observationally equivalent.
 */
@Tag('unit')
class CollectListSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror to = Mock()
    TypeMirror inner = Mock()
    TypeMirror elementType = Mock()
    TypeElement fluxElement = Mock()
    TypeMirror fluxOfElement = Mock()

    def 'declines a target that is not a Mono'() {
        when:
        def specs = new CollectList().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines a Mono target whose inner type is not a List'() {
        when:
        def specs = new CollectList().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isList(inner) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines when reactor-core Flux is not on the compile classpath'() {
        when:
        def specs = new CollectList().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isList(inner) >> true
        1 * ctx.typeArgument(inner, 0) >> elementType
        1 * ctx.typeElementNamed(Reactors.FLUX) >> null
        0 * _

        expect:
        specs.empty
    }

    def 'emits a collectList operation converting a concrete Flux<T> into Mono<List<T>>'() {
        when:
        def specs = new CollectList().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.MONO) >> true
        1 * ctx.typeArgument(to, 0) >> inner
        1 * ctx.isList(inner) >> true
        1 * ctx.typeArgument(inner, 0) >> elementType
        1 * ctx.typeElementNamed(Reactors.FLUX) >> fluxElement
        1 * ctx.declaredType(fluxElement, elementType) >> fluxOfElement
        0 * _

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'collectList'
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.CONTAINER
        spec.childScope.empty
        spec.ports.size() == 1
        spec.ports[0].name == 'flux'
        spec.ports[0].type.is(fluxOfElement)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(to)
        spec.outputNullness == Nullability.NON_NULL
        CodeBlock.of('$L\n', ((OperationCodegen) spec.codegen).render(singleInput(CodeBlock.of('$N', 'source'))))
                .toString() == 'source.collectList()\n'
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
