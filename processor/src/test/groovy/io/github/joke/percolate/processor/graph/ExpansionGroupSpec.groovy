package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ExpansionGroupSpec extends Specification {

    private static final GroupCodegen NOOP_CODEGEN = { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }

    def 'factory constructs a view containing root, slots, and initial edges'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot1 = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('a')), scope)
        def slot2 = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope)
        graph.addNode(root)
        graph.addNode(slot1)
        graph.addNode(slot2)
        def edge1 = Edge.realised(slot1, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        def edge2 = Edge.realised(slot2, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(edge1)
        graph.addEdge(edge2)

        when:
        def group = ExpansionGroup.of(root, [slot1, slot2], NOOP_CODEGEN, 'test.Strategy', [edge1, edge2].toSet(), graph)

        then:
        group.view.vertexSet() == [root, slot1, slot2].toSet()
        group.view.edgeSet() == [edge1, edge2].toSet()
        group.contains(edge1)
        group.contains(edge2)
    }

    def 'view does not auto-grow when chain edges are added outside slot-incoming chain'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        graph.addNode(source)
        def slotEdge = Edge.realised(slot, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(slotEdge)
        def group = ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.Strategy', [slotEdge].toSet(), graph)

        when:
        // Add a chain edge from source to slot — this is OUTSIDE the slot-incoming chain of the group
        def chainEdge = Edge.realised(source, slot, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(chainEdge)

        then:
        !group.contains(chainEdge)
        graph.edges().toList().contains(chainEdge)
    }

    def 'view shares vertex identity with the underlying graph'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def edge = Edge.realised(slot, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(edge)

        when:
        def group = ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.Strategy', [edge].toSet(), graph)

        then:
        group.root.is(root)
        group.slots[0].is(slot)
    }

    def 'factory throws when root is not a vertex of parent graph'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)

        when:
        ExpansionGroup.of(root, [], NOOP_CODEGEN, 'test.Strategy', [].toSet(), graph)

        then:
        thrown(IllegalArgumentException)
    }

    def 'factory throws when initial edge is not REALISED'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def markerEdge = Edge.marker(slot, root, 'test.Strategy')
        graph.addEdge(markerEdge)

        when:
        ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.Strategy', [markerEdge].toSet(), graph)

        then:
        thrown(IllegalArgumentException)
    }
}
