package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * The generic, kind-free element transform over {@code Stream<T>} (design D3/D7) — a <b>functor lift</b>. For a
 * {@code Stream<B>} demand it emits a scope-owning {@code map} (child {@code A -> B}) and {@code flatMap}
 * (child {@code A -> Stream<B>}) whose input port is the <b>type-variable</b> {@code Stream<A>}. It reads no
 * candidate: the element {@code A} is grounded by the engine (see {@code GroundingSpec}), so the strategy is purely
 * target-driven. Cross-kind and flatten emerge from composing this with each container's own {@code iterate}.
 */
@Tag('unit')
class StreamMapSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror listOfString
    @Shared TypeMirror streamOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection', 'java.util.List'].each {
            TypeUniverse.elements().getTypeElement(it)
        }
        listOfString = TypeUniverse.LIST_OF_STRING
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
    }

    def 'a Stream<B> demand emits scope-owning map and flatMap over a type-variable Stream<A> port'() {
        given: 'the expected input port template: an App(Stream, [Var 0])'
        def streamErasure = ctx.elements().getTypeElement('java.util.stream.Stream')
        def expectedTemplate = PortType.app(streamErasure, [PortType.variable(0)])

        when:
        def specs = new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'exactly a map and a flatMap'
        specs.size() == 2

        and: 'map: child A -> String (B from the target) over the type-variable Stream<A> port'
        def map = specs.find { ctx.types().isSameType(it.childScope.get().elementOut, TypeUniverse.STRING) }
        map != null
        map.codegen instanceof ScopeCodegen
        map.weight == Weights.CONTAINER
        ctx.types().isSameType(map.outputType, streamOfString)
        map.ports[0].name == 'stream'
        map.ports[0].template == expectedTemplate
        map.childScope.get().elementInTemplate == PortType.variable(0)
        ((ScopeCodegen) map.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b'))
                .toString().contains('.map(')

        and: 'flatMap: child A -> Stream<String>'
        def flatMap = specs.find { ctx.types().isSameType(it.childScope.get().elementOut, streamOfString) }
        flatMap != null
        flatMap.ports[0].template == expectedTemplate
        flatMap.childScope.get().elementInTemplate == PortType.variable(0)
        ((ScopeCodegen) flatMap.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b'))
                .toString().contains('.flatMap(')
    }

    def 'the source port is a type-variable template, never a pre-resolved concrete type (work-list stays concrete)'() {
        when:
        def specs = new StreamMap().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'every emitted port carries the App template — grounding, not the strategy, supplies the concrete source'
        specs.every { it.ports[0].template instanceof PortType.App }
    }

    def 'declines when the target is not a Stream'() {
        expect:
        new StreamMap().expand(Demands.forTarget(listOfString), ctx).toList().empty
    }
}
