package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link StreamMap} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): every seam question is stubbed on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class StreamMapSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement streamElement = Mock()
    TypeMirror streamOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror streamRawType = Mock()
    TypeMirror listOfString = Mock()

    def 'a Stream<B> demand emits scope-owning map and flatMap over a type-variable Stream<A> port'() {
        ctx.isStream(streamOfString) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.typeArgument(streamOfString, 0) >> stringType
        streamElement.asType() >> streamRawType
        def expectedTemplate = PortType.app(streamElement, [PortType.variable(0)])

        when:
        def specs = new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'exactly a map and a flatMap'
        specs.size() == 2

        and: 'map: child A -> String (B from the target) over the type-variable Stream<A> port'
        def map = specs.find { it.childScope.get().elementOut.is(stringType) }
        map != null
        map.codegen instanceof ScopeCodegen
        map.weight == Weights.CONTAINER
        map.outputType.is(streamOfString)
        map.ports[0].name == 'stream'
        map.ports[0].template == expectedTemplate
        map.childScope.get().elementInTemplate == PortType.variable(0)
        CodeBlock.of('$L\n', ((ScopeCodegen) map.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b')))
                .toString().contains('.map(')

        and: 'flatMap: child A -> Stream<String>'
        def flatMap = specs.find { it.childScope.get().elementOut.is(streamOfString) }
        flatMap != null
        flatMap.ports[0].template == expectedTemplate
        flatMap.childScope.get().elementInTemplate == PortType.variable(0)
        CodeBlock.of('$L\n', ((ScopeCodegen) flatMap.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b')))
                .toString().contains('.flatMap(')
    }

    def 'the source port is a type-variable template, never a pre-resolved concrete type (work-list stays concrete)'() {
        ctx.isStream(streamOfString) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.typeArgument(streamOfString, 0) >> stringType
        streamElement.asType() >> streamRawType

        when:
        def specs = new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'every emitted port carries the App template — grounding, not the strategy, supplies the concrete source'
        specs.every { it.ports[0].template instanceof PortType.App }
    }

    def 'declines when the target is not a Stream'() {
        ctx.isStream(listOfString) >> false

        expect:
        new StreamMap().expand(Demands.forTarget(listOfString), ctx).toList().empty
    }

    def 'declines when java.util.stream.Stream itself is not resolvable'() {
        ctx.isStream(streamOfString) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> null

        expect:
        new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList().empty
    }

    def 'the flatMap child element-out is the demanded target itself (flattening a wrapper element stream)'() {
        ctx.isStream(streamOfString) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.typeArgument(streamOfString, 0) >> stringType
        streamElement.asType() >> streamRawType

        when:
        def specs = new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList()

        then:
        def flatMap = specs.find { it.label == 'flatMap' }
        flatMap.childScope.get().elementOut.is(streamOfString)

        and: 'map is labeled map, flatMap is labeled flatMap'
        specs*.label as Set == ['map', 'flatMap'] as Set
    }
}
