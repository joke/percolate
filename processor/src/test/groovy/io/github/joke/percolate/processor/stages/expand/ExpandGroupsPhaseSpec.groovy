package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.BridgeStep
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class ExpandGroupsPhaseSpec extends Specification {

    private static final GroupCodegen NOOP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    def 'all groups are processed even when some fail'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        graph.addNode(source)

        // Group A: will fail (LONG slot, no producer)
        def rootA = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('')), scope)
        def slotA = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('a')), scope)
        graph.addNode(rootA)
        graph.addNode(slotA)
        def edgeA = Edge.realised(slotA, rootA, 1, { _, _ -> CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(edgeA)
        graph.addGroup(ExpansionGroup.of(rootA, [slotA], NOOP_CODEGEN, 'test.GroupTarget', [edgeA].toSet(), graph))

        // Group B: will fail (INT slot, no producer)
        def rootB = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('rb')), scope)
        def slotB = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('b')), scope)
        graph.addNode(rootB)
        graph.addNode(slotB)
        def edgeB = Edge.realised(slotB, rootB, 1, { _, _ -> CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(edgeB)
        graph.addGroup(ExpansionGroup.of(rootB, [slotB], NOOP_CODEGEN, 'test.GroupTarget', [edgeB].toSet(), graph))

        when:
        def result = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of())

        then:
        // Both groups should produce diagnostics
        result.diagnostics().size() == 2
    }

    def 'container-map commit emits an iteration REALISED edge from candidate to element-seed input'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def listType = TypeUniverse.LIST_OF_STRING
        def source = new Node(Optional.of(listType), new SourceLocation(AccessPath.of('xs')), scope)
        def root = new Node(Optional.of(listType), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(listType), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(source)
        graph.addNode(root)
        graph.addNode(slot)
        def slotEdge = Edge.realised(slot, root, 1, { _, _ -> CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def result = ExpansionHarness.expand(graph, List.of(new ElementSeedBridge()), List.of())
        def expanded = result.expandedGraph()

        then: 'outer REALISED edge from source to slot via the bridge'
        def outerEdges = expanded.edges().filter {
            it.kind == EdgeKind.REALISED &&
                    it.from.is(source) &&
                    it.to.is(slot) &&
                    it.strategyClassFqn.orElse(null) == ElementSeedBridge.name
        }.toList()
        outerEdges.size() == 1

        and: 'iteration REALISED edge from candidate to element-seed input'
        def iterationEdges = expanded.edges().filter {
            it.kind == EdgeKind.REALISED &&
                    it.from.is(source) &&
                    it.to.loc instanceof ElementLocation &&
                    it.strategyClassFqn.orElse(null) == ElementSeedBridge.name
        }.toList()
        iterationEdges.size() == 1
        iterationEdges[0].weight == ElementSeedBridge.WEIGHT

        and: 'the nested ExpansionGroup has root=elemTo and slots=[elemFrom]'
        def nestedGroup = expanded.groups()
                .filter { it.strategyClassFqn == ElementSeedBridge.name }
                .findFirst()
                .get()
        nestedGroup.root.loc instanceof ElementLocation
        nestedGroup.slots.size() == 1
        nestedGroup.slots[0].loc instanceof ElementLocation
        // The slot (elemFrom) is the same node as the iteration edge's destination
        nestedGroup.slots[0].is(iterationEdges[0].to)
    }

    def 'group with already-reachable slot records SAT'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(source)
        graph.addNode(root)
        graph.addNode(slot)
        // Pre-existing REALISED chain from source to slot
        def reachEdge = Edge.realised(source, slot, 1, { _, _ -> CodeBlock.of('') }, 'test.PreExisting')
        graph.addEdge(reachEdge)
        def slotEdge = Edge.realised(slot, root, 1, { _, _ -> CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def result = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of())

        then:
        // No diagnostics — the group is SAT because source → slot exists via REALISED
        result.diagnostics().empty
    }

    private static final class ElementSeedBridge implements Bridge {
        static final int WEIGHT = Weights.CONTAINER
        private static final EdgeCodegen NO_OP = { vars, inputs -> CodeBlock.of('') }

        @Override
        Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
            if (!ctx.types().isSameType(from, TypeUniverse.LIST_OF_STRING)
                    || !ctx.types().isSameType(to, TypeUniverse.LIST_OF_STRING)) {
                return Stream.empty()
            }
            def seed = new ElementSeed('element', TypeUniverse.STRING, TypeUniverse.STRING)
            Stream.of(new BridgeStep(from, to, WEIGHT, NO_OP, [seed]))
        }
    }
}
