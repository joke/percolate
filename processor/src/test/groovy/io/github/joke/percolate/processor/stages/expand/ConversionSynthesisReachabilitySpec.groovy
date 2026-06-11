package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

/**
 * Engine-level coverage of base-case reachability SAT (design E3): a node is satisfied only when a complete
 * realised path from a base case feeds it (no first-edge premature SAT). Conversion-node <em>synthesis</em>
 * (the multi-hop CONVERSION fold) is deferred to the type-conversion change and is not exercised here.
 */
@Tag('unit')
class ConversionSynthesisReachabilitySpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def applier = new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements()))
    def state = new ExpansionStateImpl(graph, applier)
    def slotResolver = new SlotResolver(new FrontierMatcher([], new InputAllocator(ctx), ctx))

    def 'reachability requires a complete realised path to a base case (no first-edge premature SAT)'() {
        given: 'a base-case source B -> M -> T, and an unproducible D -> T2'
        def base = new Node(Optional.of(TypeUniverse.INT), new SourceLocation(AccessPath.of('i')), scope)
        def mid = new Node(Optional.of(TypeUniverse.LONG), new ElementLocation('mid'), scope)
        def top = new Node(Optional.of(TypeUniverse.LONG_TYPE), new ElementLocation('top'), scope)
        def dead = new Node(Optional.of(TypeUniverse.LONG), new ElementLocation('dead'), scope)
        def top2 = new Node(Optional.of(TypeUniverse.LONG_TYPE), new ElementLocation('top2'), scope)
        [base, mid, top, dead, top2].each { graph.addNode(it) }
        graph.addEdge(base, mid, Edge.realised(Weights.STEP, EDGE_NOOP, 'test.W'))
        graph.addEdge(mid, top, Edge.realised(Weights.STEP, EDGE_NOOP, 'test.B'))
        graph.addEdge(dead, top2, Edge.realised(Weights.STEP, EDGE_NOOP, 'test.B'))

        and: 'a group whose view holds the whole chain (every node tagged with the group id)'
        def id = GroupId.next(false)
        def group = new ExpansionGroup(id, top, graph)
        [base, mid, top, dead, top2].each { it.joinGroup(id) }
        graph.addGroup(group)

        expect: 'T is reachable through the complete B -> M -> T path'
        slotResolver.reachable(top, group, state)

        and: 'T2 is NOT reachable: its sole producer D has no path to a base case (D itself is unreachable)'
        !slotResolver.reachable(dead, group, state)
        !slotResolver.reachable(top2, group, state)
    }
}
