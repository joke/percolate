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
 * {@link FluxFromStream} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac. Strict interaction
 * counts (each ending {@code 0 * _}) pin the guard chain precisely.
 */
@Tag('unit')
class FluxFromStreamSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror to = Mock()
    TypeMirror elementType = Mock()
    TypeElement streamElement = Mock()
    TypeMirror streamOfElement = Mock()

    def 'declines a target that is not a Flux'() {
        when:
        def specs = new FluxFromStream().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.FLUX) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines when java.util.stream.Stream is not on the compile classpath'() {
        when:
        def specs = new FluxFromStream().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.FLUX) >> true
        1 * ctx.typeArgument(to, 0) >> elementType
        1 * ctx.typeElementNamed('java.util.stream.Stream') >> null
        0 * _

        expect:
        specs.empty
    }

    def 'emits a fromStream operation converting a concrete Stream<T> into Flux<T>'() {
        when:
        def specs = new FluxFromStream().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isType(to, Reactors.FLUX) >> true
        1 * ctx.typeArgument(to, 0) >> elementType
        1 * ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        1 * ctx.declaredType(streamElement, elementType) >> streamOfElement
        0 * _

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'fromStream'
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.CONTAINER
        spec.childScope.empty
        spec.ports.size() == 1
        spec.ports[0].name == 'stream'
        spec.ports[0].type.is(streamOfElement)
        spec.ports[0].nullness == Nullability.NON_NULL
        spec.outputType.is(to)
        spec.outputNullness == Nullability.NON_NULL
        ((OperationCodegen) spec.codegen).render(singleInput(CodeBlock.of('$N', 'source'))).toString() ==
                'reactor.core.publisher.Flux.fromStream(source)'
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
