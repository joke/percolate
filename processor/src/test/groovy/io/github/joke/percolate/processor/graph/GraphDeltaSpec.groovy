package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@Tag('unit')
class GraphDeltaSpec extends Specification {

    Scope scope = Mock(Scope)
    Location loc = Mock(Location)

    def setup() {
        scope.encode() >> 'map()'
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
    }

    def 'of constructs delta with given nodes and edges'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

        when:
        def delta = GraphDelta.of([node], [edge])

        then:
        delta.nodeList == [node]
        delta.edgeList == [edge]
        delta.groupRegistrations.isEmpty()
    }

    def 'of with groupRegistrations includes them in the delta'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def registration = new GroupRegistration('g1', Mock(GroupCodegen))

        when:
        def delta = GraphDelta.of([node], [edge], [registration])

        then:
        delta.nodeList == [node]
        delta.edgeList == [edge]
        delta.groupRegistrations == [registration]
    }

    def 'empty returns a shared immutable empty delta'() {
        when:
        def d1 = GraphDelta.empty()
        def d2 = GraphDelta.empty()

        then:
        d1.nodeList.isEmpty()
        d1.edgeList.isEmpty()
        d1.groupRegistrations.isEmpty()
        d1 == d2
    }

    def 'nodes convenience factory creates node-only delta'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

        when:
        def delta = GraphDelta.nodes(node)

        then:
        delta.nodeList == [node]
        delta.edgeList.isEmpty()
        delta.groupRegistrations.isEmpty()
    }

    def 'edges convenience factory creates edge-only delta'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

        when:
        def delta = GraphDelta.edges(edge)

        then:
        delta.nodeList.isEmpty()
        delta.edgeList == [edge]
        delta.groupRegistrations.isEmpty()
    }

    def 'getNodes returns unmodifiable list'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def delta = GraphDelta.of([node], [])

        when:
        delta.nodeList.add(new Node(Optional.of(typeMirror), loc, scope, Optional.empty()))

        then:
        thrown(UnsupportedOperationException)
    }

    def 'getEdges returns unmodifiable list'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def delta = GraphDelta.of([], [edge])

        when:
        delta.edgeList.add(edge)

        then:
        thrown(UnsupportedOperationException)
    }

    def 'getGroupRegistrations returns unmodifiable list'() {
        given:
        def registration = new GroupRegistration('g1', Mock(GroupCodegen))
        def delta = GraphDelta.of([], [], [registration])

        when:
        delta.groupRegistrations.add(new GroupRegistration('g2', Mock(GroupCodegen)))

        then:
        thrown(UnsupportedOperationException)
    }

    def 'empty delta getNodes, getEdges and getGroupRegistrations are empty'() {
        expect:
        GraphDelta.empty().nodeList.isEmpty()
        GraphDelta.empty().edgeList.isEmpty()
        GraphDelta.empty().groupRegistrations.isEmpty()
    }

    def 'equals compares nodes, edges and groupRegistrations'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def registration = new GroupRegistration('g1', Mock(GroupCodegen))
        def d1 = GraphDelta.of([node], [edge], [registration])
        def d2 = GraphDelta.of([node], [edge], [registration])
        def d3 = GraphDelta.of([node], [edge])

        expect:
        d1 == d2
        d1 != d3
        d2 != d3
    }

    def 'hashCode is consistent with equals'() {
        given:
        def typeMirror = mockTypeMirror('String')
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def edge = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def registration = new GroupRegistration('g1', Mock(GroupCodegen))
        def d1 = GraphDelta.of([node], [edge], [registration])
        def d2 = GraphDelta.of([node], [edge], [registration])

        expect:
        d1.hashCode() == d2.hashCode()
    }

    private TypeMirror mockTypeMirror(String typeName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> typeName
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        typeMirror
    }
}
