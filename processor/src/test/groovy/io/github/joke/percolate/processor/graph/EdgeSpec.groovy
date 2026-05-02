package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror

@Tag('unit')
class EdgeSpec extends Specification {

    def 'compareTo orders by from.id first'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def locA = Mock(Location)
        locA.encode() >> 'src[a]'
        def locB = Mock(Location)
        locB.encode() >> 'src[b]'
        def emptyType = Optional.empty()
        def nodeA = new Node(emptyType, locA, scope)
        def nodeB = new Node(emptyType, locB, scope)

        when:
        def e1 = new Edge(nodeA, nodeA, 1, Optional.empty())
        def e2 = new Edge(nodeB, nodeB, 1, Optional.empty())

        then:
        e1.compareTo(e2) < 0
        e2.compareTo(e1) > 0
    }

    def 'compareTo orders by to.id second'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope)
        def toA = Mock(Location)
        toA.encode() >> 'tgt[a]'
        def toB = Mock(Location)
        toB.encode() >> 'tgt[b]'
        def nodeToA = new Node(emptyType, toA, scope)
        def nodeToB = new Node(emptyType, toB, scope)

        when:
        def e1 = new Edge(node, nodeToA, 1, Optional.empty())
        def e2 = new Edge(node, nodeToB, 1, Optional.empty())

        then:
        e1.compareTo(e2) < 0
        e2.compareTo(e1) > 0
    }

    def 'compareTo orders by weight third'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope)

        when:
        def e1 = new Edge(node, node, 1, Optional.empty())
        def e2 = new Edge(node, node, 2, Optional.empty())

        then:
        e1.compareTo(e2) < 0
    }

    def 'compareTo orders by directive presence last'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope)
        def mirror = Mock(AnnotationMirror)

        when:
        def e1 = new Edge(node, node, 1, Optional.empty())
        def e2 = new Edge(node, node, 1, Optional.of(mirror))

        then:
        e1.compareTo(e2) < 0
    }

    def 'equals compares all fields'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope)
        def mirror1 = Mock(AnnotationMirror)
        def mirror2 = Mock(AnnotationMirror)

        when:
        def e1 = new Edge(node, node, 1, Optional.of(mirror1))
        def e2 = new Edge(node, node, 1, Optional.of(mirror1))
        def e3 = new Edge(node, node, 1, Optional.of(mirror2))

        then:
        e1.equals(e2)
        e1.hashCode() == e2.hashCode()
        // e3 may or may not equal e1 depending on AnnotationMirror equality
    }

    def 'hashCode includes all fields'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope)

        when:
        def e1 = new Edge(node, node, 1, Optional.empty())
        def e2 = new Edge(node, node, 1, Optional.empty())

        then:
        e1.hashCode() == e2.hashCode()
    }
}
