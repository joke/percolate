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
        loc.segment() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

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
        loc.segment() >> 'tgt[]'

        when:
        def node = new Node(Optional.empty(), loc, scope, Optional.empty())

        then:
        node.id() == 'map()::tgt[]::?'
    }

    def 'id() uses none for null loc'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'

        when:
        def node = new Node(Optional.empty(), null, scope, Optional.empty())

        then:
        node.id() == 'map()::none::?'
    }

    def 'two nodes with equal data have equal ids'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map(Person)'
        def loc = Mock(Location)
        loc.encode() >> 'src[person]'
        loc.segment() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def n1 = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())
        def n2 = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

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
        loc.segment() >> 'src[person]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'

        when:
        def n1 = new Node(Optional.of(typeMirror), loc, scope1, Optional.empty())
        def n2 = new Node(Optional.of(typeMirror), loc, scope2, Optional.empty())

        then:
        n1.id() != n2.id()
    }

    def 'compareTo orders by id'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def locA = Mock(Location)
        locA.encode() >> 'src[a]'
        locA.segment() >> 'src[a]'
        def locB = Mock(Location)
        locB.encode() >> 'src[b]'
        locB.segment() >> 'src[b]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'

        when:
        def nA = new Node(Optional.of(typeMirror), locA, scope, Optional.empty())
        def nB = new Node(Optional.of(typeMirror), locB, scope, Optional.empty())

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
        loc.segment() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

        expect:
        node.equals(node)
    }

    def 'equals returns false for null'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

        expect:
        !node.equals(null)
    }

    def 'equals returns false for non-Node'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new Node(Optional.of(typeMirror), loc, scope, Optional.empty())

        expect:
        !node.equals('not a node')
    }

    def 'phantom node id derives from parent'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map(Foo)'
        def parentLoc = Mock(Location)
        parentLoc.encode() >> 'src[input]'
        parentLoc.segment() >> 'src[input]'
        def parentType = Mock(javax.lang.model.type.TypeMirror)
        parentType.toString() >> 'Foo'
        def parent = new Node(Optional.of(parentType), parentLoc, scope, Optional.empty())
        parent.id() >> 'map(Foo)::src[input]::Foo'
        def phantomLoc = new ElementLocation()

        when:
        def phantomType = Mock(javax.lang.model.type.TypeMirror)
        phantomType.toString() >> 'String'
        def phantom = new Node(Optional.of(phantomType), phantomLoc, scope, Optional.of(parent))

        then:
        phantom.id() == 'map(Foo)::src[input]::Foo::elem(element)::String'
    }

    def 'phantom node without parent throws on id()'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def phantomLoc = new ElementLocation()

        when:
        def phantom = new Node(Optional.empty(), phantomLoc, scope, Optional.empty())
        phantom.id()

        then:
        thrown(Exception)
    }

    def 'two phantoms with different parents have different ids'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map(Foo)'
        def phantomLoc = new ElementLocation()
        def parent1Loc = Mock(Location)
        parent1Loc.encode() >> 'src[a]'
        parent1Loc.segment() >> 'src[a]'
        def parent1Type = Mock(javax.lang.model.type.TypeMirror)
        parent1Type.toString() >> 'A'
        def parent1 = new Node(Optional.of(parent1Type), parent1Loc, scope, Optional.empty())
        parent1.id() >> 'map(Foo)::src[a]::A'
        def parent2Loc = Mock(Location)
        parent2Loc.encode() >> 'src[b]'
        parent2Loc.segment() >> 'src[b]'
        def parent2Type = Mock(javax.lang.model.type.TypeMirror)
        parent2Type.toString() >> 'B'
        def parent2 = new Node(Optional.of(parent2Type), parent2Loc, scope, Optional.empty())
        parent2.id() >> 'map(Foo)::src[b]::B'

        when:
        def phantomType = Mock(javax.lang.model.type.TypeMirror)
        phantomType.toString() >> 'String'
        def p1 = new Node(Optional.of(phantomType), phantomLoc, scope, Optional.of(parent1))
        def p2 = new Node(Optional.of(phantomType), phantomLoc, scope, Optional.of(parent2))

        then:
        p1.id() != p2.id()
        !p1.equals(p2)
    }
}
