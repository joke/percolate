package io.github.joke.percolate.processor.stages.expand


import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

/**
 * A box∘unbox round-trip closes an instance cycle that the {@link Applier}'s acyclicity check rejects — with no
 * type-recurrence guard. Because a CONVERSION folds an edge from an <em>existing</em> in-view candidate (it reuses
 * the value already present rather than minting a fresh one), the inverse conversion's edge re-enters a node the
 * frontier already reaches, and the bundle is dropped whole by the cycle detector.
 */
@Tag('unit')
class ConversionRoundTripSpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def applier = new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements()))
    def state = new ExpansionStateImpl(graph, applier, ctx)

    def 'an unbox CONVERSION folding back over an existing box edge is rejected as a cycle'() {
        given: 'int is already boxed to Integer (a realised box edge int -> Integer)'
        def intNode = new Node(Optional.of(TypeUniverse.INT), new ElementLocation('x'), scope)
        def integerNode = new Node(Optional.of(TypeUniverse.INTEGER), new SourceLocation(AccessPath.of('i')), scope)
        graph.addNode(intNode)
        graph.addNode(integerNode)
        def boxEdge = Edge.realised(Weights.NOOP, EDGE_NOOP, 'test.Box')
        graph.addEdge(intNode, integerNode, boxEdge)
        // Tag both nodes into the group (its view holds the box edge); no slot->root edge is synthesized here —
        // the unbox fold itself would be the (rejected) inverse edge.
        def gid = GroupId.next(false)
        def group = new ExpansionGroup(gid, intNode, graph)
        [intNode, integerNode].each { it.joinGroup(gid) }
        graph.addGroup(group)

        and: 'a strategy that unboxes Integer back to int as a CONVERSION'
        def unbox = { f, c ->
            Stream.of(ExpansionStep.conversion(new Slot('v', TypeUniverse.INTEGER, Weights.NOOP, null), TypeUniverse.INT, EDGE_NOOP, Weights.NOOP))
        } as ExpansionStrategy
        def matcher = new FrontierMatcher([unbox], new InputAllocator(ctx), ctx)

        when: 'the unbox folds from the existing Integer node (no fresh node is minted)'
        def bundles = matcher.matchAt(intNode, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.findAll { it instanceof AddNode }.empty
        def folded = bundles[0].deltas.find { it instanceof AddEdge }
        folded.from.is(integerNode)
        folded.to.is(intNode)

        when: 'applied, the round-trip int -> Integer -> int is a cycle'
        def applied = applier.apply(state, bundles)

        then: 'the whole bundle is dropped; no inverse edge lands and no recurrence guard was needed'
        applied == 0
        graph.edges().filter { it.kind == EdgeKind.REALISED && graph.getEdgeSource(it).is(integerNode) && graph.getEdgeTarget(it).is(intNode) }.toList().empty
    }
}
