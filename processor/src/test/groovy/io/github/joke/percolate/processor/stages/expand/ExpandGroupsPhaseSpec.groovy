package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

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
}
