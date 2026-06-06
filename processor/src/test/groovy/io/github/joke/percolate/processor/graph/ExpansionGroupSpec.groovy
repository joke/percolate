package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ExpansionGroupSpec extends Specification {

    def 'group exposes only id and root; view and inputs are derived from node tags'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot1 = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('a')), scope)
        def slot2 = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope)
        [root, slot1, slot2].each { graph.addNode(it) }
        def edge1 = realised(slot1, root)
        def edge2 = realised(slot2, root)
        graph.addEdge(edge1)
        graph.addEdge(edge2)

        when:
        def id = GroupId.next(false)
        def group = new ExpansionGroup(id, root, graph)
        [root, slot1, slot2].each { it.joinGroup(id) }

        then: 'a non-seed group derives its inputs from the root\'s incoming REALISED edges'
        group.id.is(id)
        group.root.is(root)
        group.view().vertexSet() == [root, slot1, slot2].toSet()
        group.view().edgeSet() == [edge1, edge2].toSet()
        group.inputs().toSet() == [slot1, slot2].toSet()

        and: 'the public surface carries no codegen/slots/strategy'
        !group.metaClass.respondsTo(group, 'getCodegen')
        !group.metaClass.respondsTo(group, 'getSlots')
        !group.metaClass.respondsTo(group, 'getStrategyClassFqn')
    }

    def 'group view excludes non-REALISED edges and does not leak across a shared boundary node (7.2)'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def person = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('person')), scope)
        def address = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('address')), scope)
        def street = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('address.street')), scope)
        [person, address, street].each { graph.addNode(it) }
        // A: person -> address (address is A's root); B: address -> street (street is B's root)
        def edgeA = realised(person, address)
        def edgeB = realised(address, street)
        graph.addEdge(edgeA)
        graph.addEdge(edgeB)

        when:
        def a = GroupId.next(false)
        def b = GroupId.next(false)
        def groupA = new ExpansionGroup(a, address, graph)
        def groupB = new ExpansionGroup(b, street, graph)
        // address is the root of A and an input of B
        person.joinGroup(a); address.joinGroup(a)
        address.joinGroup(b); street.joinGroup(b)

        then:
        groupA.view().edgeSet() == [edgeA].toSet()
        groupB.view().edgeSet() == [edgeB].toSet()
        address.groups().containsAll([a, b])
    }

    def 'a seed group derives its single input from the root\'s incoming SEED scaffolding edge'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('person')), scope)
        def tgt = new Node(Optional.empty(), new TargetLocation(TargetPath.of('name')), scope)
        [src, tgt].each { graph.addNode(it) }
        graph.addEdge(Edge.seed(src, tgt, Optional.empty(), Optional.empty()))

        when:
        def id = GroupId.next(true)
        def group = new ExpansionGroup(id, tgt, graph)
        src.joinGroup(id); tgt.joinGroup(id)

        then:
        group.seed
        group.inputs() == [src]
    }

    def 'two type-divergent leaves at one (scope, location) stay distinct and are not obtained via variableFor (7.3)'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def loc = new TargetLocation(TargetPath.of('value'))

        when: 'expansion mints two fresh leaves at the same location with different required types'
        def intLeaf = new Node(Optional.of(TypeUniverse.INT), loc, scope)
        def longLeaf = new Node(Optional.of(TypeUniverse.LONG), loc, scope)
        graph.addNode(intLeaf)
        graph.addNode(longLeaf)

        then: 'they are distinct instances'
        !intLeaf.is(longLeaf)

        and: 'variableFor returns a single canonical node, not either minted leaf'
        def canonical = graph.variableFor(scope, loc)
        !canonical.is(intLeaf)
        !canonical.is(longLeaf)
        graph.variableFor(scope, loc).is(canonical)
    }

    private static realised(from, to) {
        Edge.realised(from, to, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
    }
}
