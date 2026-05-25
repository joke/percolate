package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import javax.lang.model.type.TypeMirror

@Tag('unit')
@Timeout(30)
class ResolveTargetChainsPhaseSpec extends Specification {

    private static final String DIRECTIVE_BINDING_FQN =
            'io.github.joke.percolate.processor.stages.expand.DirectiveBinding'

    def 'type mismatch falls through to bridge search (no DirectiveBinding group)'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('mapHuman()')
        def returnRoot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def leaf = new Node(Optional.empty(), new TargetLocation(TargetPath.of('age')), scope)
        def typedIntSource = new Node(Optional.of(TypeUniverse.INTEGER),
                new SourceLocation(AccessPath.of('person').append('age')), scope)
        graph.addNode(returnRoot)
        graph.addNode(leaf)
        graph.addNode(typedIntSource)
        graph.addEdge(Edge.seedForTest(leaf, returnRoot))
        graph.addEdge(Edge.seedForTest(typedIntSource, leaf))
        // GroupTarget allocates a Long-typed slot; INTEGER source does not satisfy isSameType
        def phase = new ResolveTargetChainsPhase([new SingleSlotGroupTarget('age', TypeUniverse.LONG_TYPE)],
                HarnessResolveCtx.create())

        when:
        phase.apply(graph)

        then:
        graph.groups().noneMatch { it.strategyClassFqn == DIRECTIVE_BINDING_FQN }
    }

    def 'untyped SEED source falls through to bridge search'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('mapHuman()')
        def returnRoot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def leaf = new Node(Optional.empty(), new TargetLocation(TargetPath.of('lastName')), scope)
        def untypedSource = new Node(Optional.empty(),
                new SourceLocation(AccessPath.of('person').append('lastName')), scope)
        graph.addNode(returnRoot)
        graph.addNode(leaf)
        graph.addNode(untypedSource)
        graph.addEdge(Edge.seedForTest(leaf, returnRoot))
        graph.addEdge(Edge.seedForTest(untypedSource, leaf))
        def phase = new ResolveTargetChainsPhase([new SingleSlotGroupTarget('lastName')], HarnessResolveCtx.create())

        when:
        phase.apply(graph)

        then:
        graph.groups().noneMatch { it.strategyClassFqn == DIRECTIVE_BINDING_FQN }
    }

    def 'phase registers an ExpansionGroup per matched return root and emits slot REALISED edges'() {
        given:
        def graph = seedGraphWithTargetLeaves(['first', 'second'])
        def groupTarget = new TwoSlotGroupTarget()
        def phase = new ResolveTargetChainsPhase([groupTarget], HarnessResolveCtx.create())

        when:
        phase.apply(graph)

        then:
        graph.groups().count() == 1

        and:
        def group = graph.groups().findFirst().get()
        group.slots.size() == 2
        group.strategyClassFqn == TwoSlotGroupTarget.name

        and:
        // Each slot has a REALISED edge to the root
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 2
        realisedEdges.every { it.to.is(group.root) }
    }

    def 'phase skips when no GroupTarget matches'() {
        given:
        def graph = seedGraphWithTargetLeaves(['first'])
        def emptyTarget = new EmptyGroupTarget()
        def phase = new ResolveTargetChainsPhase([emptyTarget], HarnessResolveCtx.create())

        when:
        phase.apply(graph)

        then:
        graph.groups().count() == 0
    }

    private static MapperGraph seedGraphWithTargetLeaves(List<String> slotNames) {
        def graph = new MapperGraph()
        def scope = new HarnessScope('mapHuman()')
        def returnRoot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(returnRoot)
        for (final name : slotNames) {
            def leaf = new Node(Optional.empty(), new TargetLocation(TargetPath.of(name)), scope)
            graph.addNode(leaf)
            graph.addEdge(Edge.seedForTest(leaf, returnRoot))
        }
        graph
    }

    private static final class TwoSlotGroupTarget implements GroupTarget {
        @Override
        Optional<GroupBuild> buildFor(final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
            def slots = [
                    new Slot('first', TypeUniverse.STRING, 1, TypeUniverse.anyConstruct()),
                    new Slot('second', TypeUniverse.STRING, 1, TypeUniverse.anyConstruct())
            ]
            def codegen = { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as GroupCodegen
            Optional.of(new GroupBuild(slots, codegen))
        }
    }

    private static final class SingleSlotGroupTarget implements GroupTarget {
        private final String slotName
        private final TypeMirror slotType

        SingleSlotGroupTarget(final String slotName) {
            this(slotName, TypeUniverse.STRING)
        }

        SingleSlotGroupTarget(final String slotName, final TypeMirror slotType) {
            this.slotName = slotName
            this.slotType = slotType
        }

        @Override
        Optional<GroupBuild> buildFor(final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
            def slots = [new Slot(slotName, slotType, 1, TypeUniverse.anyConstruct())]
            def codegen = { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as GroupCodegen
            Optional.of(new GroupBuild(slots, codegen))
        }
    }

    private static final class EmptyGroupTarget implements GroupTarget {
        @Override
        Optional<GroupBuild> buildFor(final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
            Optional.empty()
        }
    }
}
