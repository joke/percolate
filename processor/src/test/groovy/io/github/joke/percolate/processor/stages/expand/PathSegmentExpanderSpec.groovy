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
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class PathSegmentExpanderSpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen GETTER = { vars, inputs -> CodeBlock.of('$L.getValue()', inputs.single()) }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    def state = new ExpansionStateImpl(graph, new Applier(resolver), ctx)

    def 'matching resolver types the root and SATs the group'() {
        given:
        def group = pathGroup(TypeUniverse.STRING)
        def segment = new ResolvedSegment(TypeUniverse.STRING, GETTER, 1, TypeUniverse.anyConstruct())
        @Subject
        def expander = new PathSegmentExpander(new PathSegmentGroupResolver([resolverReturning(segment)]), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        result.bundles.size() == 1
        def deltas = result.bundles[0].deltas
        deltas.find { it instanceof TypeNode }?.node?.is(group.root)
        deltas.find { it instanceof AddEdge }?.edge?.from?.is(group.slots[0])
        deltas.find { it instanceof AddEdge }?.edge?.to?.is(group.root)
        deltas.any { it instanceof AddEdgeToView }
    }

    def 'no matching resolver leaves the slot pending with no bundles'() {
        given:
        def group = pathGroup(TypeUniverse.STRING)
        def expander = new PathSegmentExpander(new PathSegmentGroupResolver([silentResolver()]), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.empty
        result.pendingSlots == [group.slots[0]]
    }

    def 'an untyped slot leaves the group pending without invoking resolvers'() {
        given:
        def group = pathGroup(null)
        def expander = new PathSegmentExpander(new PathSegmentGroupResolver([silentResolver()]), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.empty
        result.pendingSlots == [group.slots[0]]
    }

    private static PathSegmentResolver resolverReturning(final ResolvedSegment segment) {
        { parentType, name, c -> Optional.of(segment) } as PathSegmentResolver
    }

    private static PathSegmentResolver silentResolver() {
        { parentType, name, c -> Optional.empty() } as PathSegmentResolver
    }

    private ExpansionGroup pathGroup(final TypeMirror slotType) {
        def slot = new Node(Optional.ofNullable(slotType), new SourceLocation(AccessPath.of('bean')), scope)
        def root = new Node(Optional.empty(), new SourceLocation(AccessPath.of('bean').append('value')), scope)
        graph.addNode(slot)
        graph.addNode(root)
        ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.PathSegmentGroup', [].toSet(), graph)
    }
}
