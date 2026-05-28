package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

@Tag('unit')
class FrontierMatcherSpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def state = new ExpansionStateImpl(
            graph,
            new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())),
            ctx)

    def 'PRESERVING step reuses a same-loc candidate without allocating a node'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([bridge(ScopeTransition.PRESERVING)]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.findAll { it instanceof AddNode }.empty
        def edge = bundles[0].deltas.find { it instanceof AddEdge }.edge
        edge.from.is(candidate)
        edge.to.is(frontier)
        bundles[0].deltas.any { it instanceof AddGroup }
    }

    def 'ENTERING step fresh-allocates an input at the frontier element location'() {
        given:
        def frontier = node(new ElementLocation('e'), TypeUniverse.STRING)
        def candidate = node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([bridge(ScopeTransition.ENTERING)]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        def added = bundles[0].deltas.find { it instanceof AddNode }.node
        added.loc == new ElementLocation('e')
    }

    def 'EXITING step fresh-allocates an input at ElementLocation(role)'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([bridge(ScopeTransition.EXITING)]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.find { it instanceof AddNode }.node.loc == new ElementLocation('item')
    }

    def 'each matching bridge produces its own bundle (multi-fire siblings)'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([bridge(ScopeTransition.PRESERVING), bridge(ScopeTransition.PRESERVING)])
                .matchAt(frontier, group, state)

        then:
        bundles.size() == 2
    }

    def 'TargetLocation candidates are never considered'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new TargetLocation(TargetPath.of('t')), TypeUniverse.STRING)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([bridge(ScopeTransition.PRESERVING)]).matchAt(frontier, group, state)

        then:
        bundles.empty
    }

    private static Bridge bridge(final ScopeTransition transition) {
        { from, to, c ->
            TypeUniverse.types().isSameType(to, TypeUniverse.STRING)
                    ? Stream.of(new BridgeStep(TypeUniverse.STRING, TypeUniverse.STRING, 1, EDGE_NOOP, transition, 'item'))
                    : Stream.empty()
        } as Bridge
    }

    private FrontierMatcher matcher(final List<Bridge> bridges) {
        new FrontierMatcher(bridges, new InputAllocator(ctx), ctx)
    }

    private Node node(final Location loc, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private ExpansionGroup group(final Node root, final Node slot) {
        graph.addNode(root)
        graph.addNode(slot)
        ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
    }
}
