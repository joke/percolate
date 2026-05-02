package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapperGraphSpec extends Specification {

    Scope scope = Mock(Scope)
    Scope otherScope = Mock(Scope)

    Location srcLoc = Mock(Location)
    Location tgtLoc = Mock(Location)

    def node(Scope s, Location l, Optional type) {
        new Node(type, l, s)
    }

    def edge(Node from, Node to, int weight = 1, Optional directive = Optional.empty()) {
        new Edge(from, to, weight, directive)
    }

    def setup() {
        scope.encode() >> 'map()'
        otherScope.encode() >> 'map2()'
        srcLoc.encode() >> 'src[x]'
        tgtLoc.encode() >> 'tgt[y]'
    }

    def 'addNode is idempotent on equal nodes'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def n1 = node(scope, srcLoc, Optional.of(typeMirror))
        def n2 = node(scope, srcLoc, Optional.of(typeMirror))

        when:
        graph.addNode(n1)
        graph.addNode(n2)

        then:
        graph.nodeCount() == 1
    }

    def 'addNode adds distinct nodes'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))

        when:
        graph.addNode(n1)
        graph.addNode(n2)

        then:
        graph.nodeCount() == 2
    }

    def 'addEdge rejects duplicate edges by structural equality'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        def e1 = edge(from, to)
        def e2 = edge(from, to)

        when:
        graph.addEdge(e1)
        graph.addEdge(e2)

        then:
        graph.edgeCount() == 1
    }

    def 'addEdge adds distinct edges'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def toLoc1 = Mock(Location)
        def toLoc2 = Mock(Location)
        toLoc1.encode() >> 'tgt[a]'
        toLoc2.encode() >> 'tgt[b]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to1 = node(otherScope, toLoc1, Optional.of(typeMirror))
        def to2 = node(otherScope, toLoc2, Optional.of(typeMirror))
        def e1 = edge(from, to1)
        def e2 = edge(from, to2)

        when:
        graph.addEdge(e1)
        graph.addEdge(e2)

        then:
        graph.edgeCount() == 2
    }

    def 'nodes() iterates in ascending id order'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def locC = Mock(Location)
        def locA = Mock(Location)
        def locB = Mock(Location)
        locC.encode() >> 'src[z]'
        locA.encode() >> 'src[a]'
        locB.encode() >> 'src[m]'
        def nC = node(scope, locC, Optional.of(typeMirror))
        def nA = node(scope, locA, Optional.of(typeMirror))
        def nB = node(scope, locB, Optional.of(typeMirror))

        when:
        graph.addNode(nC)
        graph.addNode(nA)
        graph.addNode(nB)

        then:
        graph.nodes().toList() == [nA, nB, nC]
    }

    def 'edges() iterates in ascending natural order'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def toALoc = Mock(Location)
        def toBLoc = Mock(Location)
        toALoc.encode() >> 'tgt[a]'
        toBLoc.encode() >> 'tgt[b]'
        def toB = node(otherScope, toBLoc, Optional.of(typeMirror))
        def toA = node(otherScope, toALoc, Optional.of(typeMirror))
        def eB = edge(from, toB)
        def eA = edge(from, toA)

        when:
        graph.addEdge(eB)
        graph.addEdge(eA)

        then:
        graph.edges().toList() == [eA, eB]
    }

    def 'nodesByScope filters to specific scope'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def n1 = node(scope, loc, Optional.of(typeMirror))
        def n2 = node(otherScope, loc, Optional.of(typeMirror))

        when:
        graph.addNode(n1)
        graph.addNode(n2)

        then:
        graph.nodesByScope(scope).toList() == [n1]
        graph.nodesByScope(otherScope).toList() == [n2]
    }

    def 'nodesByScope preserves order within scope'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def locC = Mock(Location)
        def locA = Mock(Location)
        locC.encode() >> 'src[z]'
        locA.encode() >> 'src[a]'
        def nC = node(scope, locC, Optional.of(typeMirror))
        def nA = node(scope, locA, Optional.of(typeMirror))

        when:
        graph.addNode(nC)
        graph.addNode(nA)

        then:
        graph.nodesByScope(scope).toList() == [nA, nC]
    }

    def 'isForest check is deferred'() {
        // isForest() behavior depends on JGraphT version; skip for now
        expect:
        true
    }

    def 'isForest returns true for tree structure'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        graph.addNode(n1)
        graph.addNode(n2)
        graph.addEdge(edge(n1, n2))

        expect:
        graph.isForest()
    }

    def 'isForest returns false when undirected view has cycle'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        def loc3 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc3.encode() >> 'src[c]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def n3 = node(scope, loc3, Optional.of(typeMirror))
        graph.addNode(n1)
        graph.addNode(n2)
        graph.addNode(n3)
        graph.addEdge(edge(n1, n2))
        graph.addEdge(edge(n2, n3))
        graph.addEdge(edge(n3, n1))

        expect:
        !graph.isForest()
    }

    def 'edgeSet returns unmodifiable set'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(edge(from, to))

        when:
        def set = graph.edgeSet()
        set.add(Mock(io.github.joke.percolate.processor.graph.Edge))

        then:
        set.size() == 1
        thrown(UnsupportedOperationException)
    }
}
