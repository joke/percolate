package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

/**
 * Engine-level coverage of conversion-node synthesis (design E1/E2) and base-case reachability SAT (E3):
 * a CONVERSION whose input type is absent from the view synthesizes a type-keyed conversion frontier, and a
 * node is satisfied only when a complete realised path from a base case feeds it (no first-edge premature SAT).
 */
@Tag('unit')
class ConversionSynthesisReachabilitySpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def applier = new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements()))
    def state = new ExpansionStateImpl(graph, applier, ctx)
    def slotResolver = new SlotResolver(new FrontierMatcher([], new InputAllocator(ctx), ctx))

    def 'a CONVERSION whose input type is absent synthesizes a type-keyed conversion frontier'() {
        given: 'a Long frontier with no long-typed value in view'
        def frontier = new Node(Optional.of(TypeUniverse.LONG_TYPE), new ElementLocation('age'), scope)
        graph.addNode(frontier)
        def group = ExpansionGroup.of(frontier, [], GROUP_NOOP, 'test.G', [].toSet(), graph)

        and: 'a box strategy: to produce Long, consume long'
        def box = { f, c ->
            Stream.of(ExpansionStep.conversion(
                    new Slot('v', TypeUniverse.LONG, Weights.NOOP, null), TypeUniverse.LONG_TYPE, EDGE_NOOP, Weights.STEP))
        } as ExpansionStrategy
        def matcher = new FrontierMatcher([box], new InputAllocator(ctx), ctx)

        when:
        def bundles = matcher.matchAt(frontier, group, state)

        then: 'a fresh long node is synthesized and registered as a conversion frontier'
        bundles.size() == 1
        def added = bundles[0].deltas.findAll { it instanceof AddNode }
        added.size() == 1
        ctx.types().isSameType(added[0].node.type.get(), TypeUniverse.LONG)
        def registered = bundles[0].deltas.findAll { it instanceof RegisterConversionFrontier }
        registered.size() == 1
        registered[0].node.is(added[0].node)

        and: 'the realised edge runs from the synthesized node into the frontier'
        def edge = bundles[0].deltas.find { it instanceof AddEdge }.edge
        edge.to.is(frontier)
        edge.from.is(added[0].node)

        when: 'applied, the synthesized node joins the group view and its conversion-frontier set'
        applier.apply(state, bundles)

        then:
        group.conversionFrontiers.size() == 1
        group.conversionFrontiers.iterator().next().is(added[0].node)
    }

    def 'reachability requires a complete realised path to a base case (no first-edge premature SAT)'() {
        given: 'a base-case source B -> M -> T, and an unproducible D -> T2'
        def base = new Node(Optional.of(TypeUniverse.INT), new SourceLocation(AccessPath.of('i')), scope)
        def mid = new Node(Optional.of(TypeUniverse.LONG), new ElementLocation('mid'), scope)
        def top = new Node(Optional.of(TypeUniverse.LONG_TYPE), new ElementLocation('top'), scope)
        def dead = new Node(Optional.of(TypeUniverse.LONG), new ElementLocation('dead'), scope)
        def top2 = new Node(Optional.of(TypeUniverse.LONG_TYPE), new ElementLocation('top2'), scope)
        [base, mid, top, dead, top2].each { graph.addNode(it) }
        def bm = Edge.realised(base, mid, Weights.STEP, EDGE_NOOP, 'test.W')
        def mt = Edge.realised(mid, top, Weights.STEP, EDGE_NOOP, 'test.B')
        def dt = Edge.realised(dead, top2, Weights.STEP, EDGE_NOOP, 'test.B')
        [bm, mt, dt].each { graph.addEdge(it) }
        def group = ExpansionGroup.of(top, [mid, base, dead, top2], GROUP_NOOP, 'test.G', [bm, mt, dt].toSet(), graph)

        expect: 'T is reachable through the complete B -> M -> T path'
        slotResolver.reachable(top, group, state)

        and: 'T2 is NOT reachable: its sole producer D has no path to a base case (D itself is unreachable)'
        !slotResolver.reachable(dead, group, state)
        !slotResolver.reachable(top2, group, state)
    }
}
