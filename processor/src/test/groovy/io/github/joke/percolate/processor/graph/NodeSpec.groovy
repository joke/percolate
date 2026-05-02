package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class NodeSpec extends Specification {

    def 'id() is deterministic for the same data'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map(Person)'
        def loc = Mock(Location)
        loc.encode() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def node = new Node(Optional.of(typeMirror), loc, scope)

        then:
        node.id() == 'map(Person)::src[person]::Person'
        node.id() == node.id()
    }

    def 'id() uses ? for empty type'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'tgt[]'

        when:
        def node = new Node(Optional.empty(), loc, scope)

        then:
        node.id() == 'map()::tgt[]::?'
    }

    def 'id() uses none for null loc'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'

        when:
        def node = new Node(Optional.empty(), null, scope)

        then:
        node.id() == 'map()::none::?'
    }

    def 'two nodes with equal data have equal ids'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map(Person)'
        def loc = Mock(Location)
        loc.encode() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def n1 = new Node(Optional.of(typeMirror), loc, scope)
        def n2 = new Node(Optional.of(typeMirror), loc, scope)

        then:
        n1.id() == n2.id()
        n1.equals(n2)
        n1.hashCode() == n2.hashCode()
    }

    def 'two nodes with different data have different ids'() {
        given:
        def scope1 = Mock(Scope)
        scope1.encode() >> 'map(Person)'
        def scope2 = Mock(Scope)
        scope2.encode() >> 'map(Address)'
        def loc = Mock(Location)
        loc.encode() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def n1 = new Node(Optional.of(typeMirror), loc, scope1)
        def n2 = new Node(Optional.of(typeMirror), loc, scope2)

        then:
        n1.id() != n2.id()
    }

    def 'compareTo orders by id'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def locA = Mock(Location)
        locA.encode() >> 'src[a]'
        def locB = Mock(Location)
        locB.encode() >> 'src[b]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'

        when:
        def nA = new Node(Optional.of(typeMirror), locA, scope)
        def nB = new Node(Optional.of(typeMirror), locB, scope)

        then:
        nA.compareTo(nB) < 0
        nB.compareTo(nA) > 0
    }

    def 'equals returns true for same object'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope)

        expect:
        node.equals(node)
    }

    def 'equals returns false for null'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope)

        expect:
        !node.equals(null)
    }

    def 'equals returns false for non-Node'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope)

        expect:
        !node.equals('not a node')
    }
}
