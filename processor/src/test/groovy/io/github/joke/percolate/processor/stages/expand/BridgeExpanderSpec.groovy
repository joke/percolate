package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

@Tag('unit')
class BridgeExpanderSpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()

    def 'group whose only slot is a parameter base case is SAT'() {
        given:
        def expander = bridgeExpander([], [])
        def root = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('ctor'), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        result.bundles.empty
    }

    def 'untyped slot with no expected type stays pending without a bundle'() {
        given:
        def expander = bridgeExpander([], [])
        def root = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('ctor'), scope)
        def slot = new Node(Optional.empty(), new ElementLocation('x'), scope, Optional.of(root))
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.empty
        result.pendingSlots == [slot]
    }

    def 'a matching bridge spawns one sub-group and leaves the slot pending'() {
        given:
        def expander = bridgeExpander([identityBridge()], [])
        def candidate = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('c')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('role'), scope)
        graph.addNode(candidate)
        graph.addNode(slot)
        def group = ExpansionGroup.of(candidate, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots == [slot]
        result.bundles.size() == 1
        result.bundles[0].deltas.any { it instanceof AddGroup }
    }

    def 'falls back to a GroupTarget build when no bridge matches'() {
        given:
        def build = new GroupBuild([new Slot('name', TypeUniverse.STRING, 1, TypeUniverse.anyConstruct())], GROUP_NOOP)
        def expander = bridgeExpander([], [groupTargetReturning(build)])
        def root = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('ctor'), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('role'), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()), ctx)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.size() == 1
        def deltas = result.bundles[0].deltas
        deltas.findAll { it instanceof AddNode }.size() == 1
        deltas.any { it instanceof AddGroup }
    }

    private static io.github.joke.percolate.processor.nullability.NullabilityResolver nullResolver() {
        new io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver(
                io.github.joke.percolate.processor.nullability.NullabilityAnnotations.jspecifyDefaults(),
                TypeUniverse.elements())
    }

    private static Bridge identityBridge() {
        { from, to, c ->
            TypeUniverse.types().isSameType(from, TypeUniverse.STRING)
                    && TypeUniverse.types().isSameType(to, TypeUniverse.STRING)
                    ? Stream.of(new BridgeStep(TypeUniverse.STRING, TypeUniverse.STRING, 1, EDGE_NOOP))
                    : Stream.empty()
        } as Bridge
    }

    private static GroupTarget groupTargetReturning(final GroupBuild build) {
        { returnType, tails, c -> Optional.of(build) } as GroupTarget
    }

    private BridgeExpander bridgeExpander(final List<Bridge> bridges, final List<GroupTarget> groupTargets) {
        def frontierMatcher = new FrontierMatcher(bridges, new InputAllocator(ctx), ctx)
        new BridgeExpander(new SlotResolver(frontierMatcher, groupTargets, ctx))
    }
}
