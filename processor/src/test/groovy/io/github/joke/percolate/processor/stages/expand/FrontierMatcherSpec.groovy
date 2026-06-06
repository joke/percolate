package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.CombinatorialMatch
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ElementScope
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
 * Behaviour of the unified {@link FrontierMatcher}: it branches on each emitted {@link ExpansionStep}'s intent —
 * a CONVERSION folds an edge from an in-view candidate into the current group (no new node, no sub-group), a
 * BOUNDARY opens a sub-group with the step's slots, and container boundaries place their input per element scope.
 * It also dedups structurally-identical emissions and never offers a TargetLocation node as a candidate.
 */
@Tag('unit')
class FrontierMatcherSpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def state = new ExpansionStateImpl(
            graph,
            new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())),
            ctx)

    def 'a CONVERSION step folds an edge from an in-view candidate into the current group — no node, no sub-group'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new SourceLocation(AccessPath.of('c')), TypeUniverse.STRING)
        def group = group(frontier, candidate)

        when:
        def bundles = matcher([conversion(TypeUniverse.STRING)]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.findAll { it instanceof AddNode }.empty
        bundles[0].deltas.findAll { it instanceof AddGroup }.empty
        def edge = bundles[0].deltas.find { it instanceof AddEdge }.edge
        edge.from.is(candidate)
        edge.to.is(frontier)
    }

    def 'a BOUNDARY step opens a sub-group rooted at the frontier with a fresh input'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG))

        when:
        def bundles = matcher([boundary(TypeUniverse.LONG, TypeUniverse.STRING)]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        def added = bundles[0].deltas.find { it instanceof AddNode }.node
        added != null
        def edge = bundles[0].deltas.find { it instanceof AddEdge }.edge
        edge.from.is(added)
        edge.to.is(frontier)
        bundles[0].deltas.any { it instanceof AddGroup }
    }

    def 'an ENTERING container boundary allocates its input at the frontier element location'() {
        given:
        // The candidate type differs from the boundary input type, so the input is freshly allocated (no reuse).
        def frontier = node(new ElementLocation('e'), TypeUniverse.STRING)
        def group = group(frontier, node(new SourceLocation(AccessPath.of('c')), TypeUniverse.INT))

        when:
        def bundles = matcher([container(TypeUniverse.LONG, TypeUniverse.STRING, ElementScope.ENTERING)])
                .matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.find { it instanceof AddNode }.node.loc == new ElementLocation('e')
    }

    def 'an EXITING container boundary allocates its input at an ElementLocation child'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG))

        when:
        def bundles = matcher([container(TypeUniverse.LONG, TypeUniverse.STRING, ElementScope.EXITING)])
                .matchAt(frontier, group, state)

        then:
        bundles.size() == 1
        bundles[0].deltas.find { it instanceof AddNode }.node.loc instanceof ElementLocation
    }

    def 'structurally-identical steps from one strategy collapse to a single bundle'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG))
        // One strategy emitting the same boundary step twice — as the per-candidate container fan-out would.
        def twin = { f, c ->
            Stream.of(boundaryStep(TypeUniverse.LONG, TypeUniverse.STRING), boundaryStep(TypeUniverse.LONG, TypeUniverse.STRING))
        } as ExpansionStrategy

        when:
        def bundles = matcher([twin]).matchAt(frontier, group, state)

        then:
        bundles.size() == 1
    }

    def 'steps that differ by input type are both kept'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def group = group(frontier, node(new SourceLocation(AccessPath.of('c')), TypeUniverse.LONG))
        def two = { f, c ->
            Stream.of(boundaryStep(TypeUniverse.LONG, TypeUniverse.STRING), boundaryStep(TypeUniverse.INT, TypeUniverse.STRING))
        } as ExpansionStrategy

        when:
        def bundles = matcher([two]).matchAt(frontier, group, state)

        then:
        bundles.size() == 2
    }

    def 'a TargetLocation node is never offered as a candidate'() {
        given:
        def frontier = node(new SourceLocation(AccessPath.of('f')), TypeUniverse.STRING)
        def candidate = node(new TargetLocation(TargetPath.of('t')), TypeUniverse.STRING)
        def group = group(frontier, candidate)
        // A combinatorial strategy only fires when it is offered a candidate.
        def combinatorial = { from, to, c ->
            Stream.of(boundaryStep(TypeUniverse.STRING, TypeUniverse.STRING))
        } as CombinatorialMatch

        when:
        def bundles = matcher([combinatorial]).matchAt(frontier, group, state)

        then:
        bundles.empty
    }

    private static ExpansionStrategy conversion(type) {
        { f, c -> Stream.of(ExpansionStep.conversion(new Slot('v', type, Weights.NOOP, null), type, EDGE_NOOP, Weights.NOOP)) } as ExpansionStrategy
    }

    private static ExpansionStrategy boundary(slotType, outType) {
        { f, c -> Stream.of(boundaryStep(slotType, outType)) } as ExpansionStrategy
    }

    private static ExpansionStrategy container(slotType, outType, ElementScope elementScope) {
        { f, c -> Stream.of(ExpansionStep.containerBoundary(new Slot('s', slotType, Weights.CONTAINER, null), outType, EDGE_NOOP, elementScope, Weights.CONTAINER)) } as ExpansionStrategy
    }

    private static ExpansionStep boundaryStep(slotType, outType) {
        ExpansionStep.boundary([new Slot('s', slotType, Weights.STEP, null)], outType, EDGE_NOOP, Weights.STEP)
    }

    private FrontierMatcher matcher(final List<ExpansionStrategy> strategies) {
        new FrontierMatcher(strategies, new InputAllocator(ctx), ctx)
    }

    private Node node(final Location loc, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private ExpansionGroup group(final Node root, final Node slot) {
        graph.addNode(root)
        graph.addNode(slot)
        TestGroups.of(root, [slot], 'test.G', [].toSet(), graph)
    }
}
