package io.github.joke.percolate.reactor

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.reactor.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link FluxMap} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac. Strict interaction
 * counts (each ending {@code 0 * _}) pin the guard chain precisely.
 */
@Tag('unit')
class FluxMapSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement fluxElement = Mock()
    TypeMirror fluxOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror fluxRawType = Mock()
    TypeMirror listOfString = Mock()

    def 'a Flux<B> demand emits scope-owning map and flatMap over a type-variable Flux<A> port'() {
        when:
        def specs = new FluxMap().expand(Demands.forTarget(fluxOfString), ctx).toList()

        then:
        1 * ctx.isType(fluxOfString, Reactors.FLUX) >> true
        1 * ctx.typeElementNamed(Reactors.FLUX) >> fluxElement
        1 * ctx.typeArgument(fluxOfString, 0) >> stringType
        1 * fluxElement.asType() >> fluxRawType
        0 * _

        expect: 'exactly a map and a flatMap'
        specs.size() == 2

        and: 'map: child A -> String (B from the target) over the type-variable Flux<A> port'
        def expectedTemplate = PortType.app(fluxElement, [PortType.variable(0)])
        def map = specs.find { it.childScope.get().elementOut.is(stringType) }
        map != null
        map.label == 'map'
        map.codegen instanceof ScopeCodegen
        map.weight == Weights.CONTAINER
        map.outputType.is(fluxOfString)
        map.ports[0].name == 'flux'
        map.ports[0].template == expectedTemplate
        map.childScope.get().elementInTemplate == PortType.variable(0)
        CodeBlock.of('$L\n', ((ScopeCodegen) map.codegen).weave(CodeBlock.of('$N', 'f'), 'v', CodeBlock.of('$N', 'b')))
                .toString() == 'f.map(v -> b)\n'

        and: 'flatMap: child A -> Flux<B> (the demanded target itself)'
        def flatMap = specs.find { it.childScope.get().elementOut.is(fluxOfString) }
        flatMap != null
        flatMap.label == 'flatMap'
        flatMap.ports[0].template == expectedTemplate
        flatMap.childScope.get().elementInTemplate == PortType.variable(0)
        CodeBlock.of('$L\n', ((ScopeCodegen) flatMap.codegen).weave(CodeBlock.of('$N', 'f'), 'v', CodeBlock.of('$N', 'b')))
                .toString() == 'f.flatMap(v -> b)\n'

        and: 'every emitted port carries the App template — grounding, not the strategy, supplies the concrete source'
        specs.every { it.ports[0].template instanceof PortType.App }
        specs.every { it.ports[0].nullness == Nullability.NON_NULL }
        specs.every { it.childScope.get().elementInNullness == Nullability.NON_NULL }
        specs.every { it.childScope.get().elementOutNullness == Nullability.NON_NULL }
    }

    def 'declines when the target is not a Flux'() {
        when:
        def specs = new FluxMap().expand(Demands.forTarget(listOfString), ctx).toList()

        then:
        1 * ctx.isType(listOfString, Reactors.FLUX) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'declines when reactor-core Flux is not on the compile classpath'() {
        when:
        def specs = new FluxMap().expand(Demands.forTarget(fluxOfString), ctx).toList()

        then:
        1 * ctx.isType(fluxOfString, Reactors.FLUX) >> true
        1 * ctx.typeElementNamed(Reactors.FLUX) >> null
        0 * _

        expect:
        specs.empty
    }
}
