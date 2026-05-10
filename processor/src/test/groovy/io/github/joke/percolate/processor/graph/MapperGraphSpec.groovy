package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class MapperGraphSpec extends Specification {

    Scope scope = Mock(Scope)
    Scope otherScope = Mock(Scope)

    Location srcLoc = Mock(Location)
    Location tgtLoc = Mock(Location)

    def node(Scope s, Location l, Optional type) {
        new Node(type, l, s, Optional.empty())
    }

    def edge(Node from, Node to, int weight = 1, Optional directive = Optional.empty()) {
        new Edge(from, to, weight, EdgeKind.SEED, directive, Optional.empty(), Optional.empty(), Optional.empty())
    }

    def realisedEdge(Node from, Node to, int weight = 1) {
        new Edge(from, to, weight, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
    }

    def setup() {
        scope.encode() >> 'map()'
        otherScope.encode() >> 'map2()'
        srcLoc.encode() >> 'src[x]'
        srcLoc.segment() >> 'src[x]'
        tgtLoc.encode() >> 'tgt[y]'
        tgtLoc.segment() >> 'tgt[y]'
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
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
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
        loc.segment() >> 'src[x]'
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
        loc.segment() >> 'src[x]'
        def toLoc1 = Mock(Location)
        def toLoc2 = Mock(Location)
        toLoc1.encode() >> 'tgt[a]'
        toLoc1.segment() >> 'tgt[a]'
        toLoc2.encode() >> 'tgt[b]'
        toLoc2.segment() >> 'tgt[b]'
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
        locC.segment() >> 'src[z]'
        locA.encode() >> 'src[a]'
        locA.segment() >> 'src[a]'
        locB.encode() >> 'src[m]'
        locB.segment() >> 'src[m]'
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
        loc.segment() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def toALoc = Mock(Location)
        def toBLoc = Mock(Location)
        toALoc.encode() >> 'tgt[a]'
        toALoc.segment() >> 'tgt[a]'
        toBLoc.encode() >> 'tgt[b]'
        toBLoc.segment() >> 'tgt[b]'
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
        loc.segment() >> 'src[x]'
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
        locC.segment() >> 'src[z]'
        locA.encode() >> 'src[a]'
        locA.segment() >> 'src[a]'
        def nC = node(scope, locC, Optional.of(typeMirror))
        def nA = node(scope, locA, Optional.of(typeMirror))

        when:
        graph.addNode(nC)
        graph.addNode(nA)

        then:
        graph.nodesByScope(scope).toList() == [nA, nC]
    }

    def 'isAcyclic returns true for tree structure'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(edge(from, to))

        expect:
        graph.isAcyclic()
    }

    def 'isAcyclic returns true for tree structure'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        graph.addNode(n1)
        graph.addNode(n2)
        graph.addEdge(edge(n1, n2))

        expect:
        graph.isAcyclic()
    }

    def 'isAcyclic returns false when directed graph has cycle'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        def loc3 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        loc3.encode() >> 'src[c]'
        loc3.segment() >> 'src[c]'
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
        !graph.isAcyclic()
    }

    def 'edgeSet returns unmodifiable set'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
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

    def 'realisedSubgraph is empty for seed-only graphs'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(edge(from, to))

        when:
        def sub = graph.realisedSubgraph()

        then:
        sub.nodes().count() == 0
        sub.edges().count() == 0
    }

    def 'realisedSubgraph filter excludes SEED edges'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(edge(from, to))
        graph.addEdge(new Edge(from, to, 1, EdgeKind.MARKER, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        graph.addEdge(new Edge(from, to, 1, EdgeKind.SUB_SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))

        when:
        def sub = graph.realisedSubgraph()

        then:
        sub.edges().count() == 0
    }

    def 'realisedSubgraph includes only nodes incident on REALISED edges'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def from = node(scope, loc, Optional.of(typeMirror))
        def to = node(otherScope, tgtLoc, Optional.of(typeMirror))
        def otherLoc = Mock(Location)
        otherLoc.encode() >> 'src[y]'
        otherLoc.segment() >> 'src[y]'
        def other = node(otherScope, otherLoc, Optional.of(typeMirror))
        graph.addNode(from)
        graph.addNode(to)
        graph.addNode(other)
        graph.addEdge(realisedEdge(from, to))

        when:
        def sub = graph.realisedSubgraph()
        def nodeIds = sub.nodes().collect { it.id() }

        then:
        nodeIds.contains(from.id())
        nodeIds.contains(to.id())
        !nodeIds.contains(other.id())
    }

    def 'addGroupCodegen rejects duplicates'() {
        given:
        def graph = new MapperGraph()
        def codegen1 = Mock(GroupCodegen)
        def codegen2 = Mock(GroupCodegen)

        when:
        graph.addGroupCodegen('group1', codegen1)
        graph.addGroupCodegen('group1', codegen2)

        then:
        thrown(IllegalStateException)
    }

    def 'groupCodegen returns the stored closure'() {
        given:
        def graph = new MapperGraph()
        def codegen = Mock(GroupCodegen)
        graph.addGroupCodegen('group1', codegen)

        when:
        def result = graph.groupCodegen('group1')

        then:
        result.isPresent()
        result.get() == codegen
    }

    def 'groupCodegen returns empty for unknown groupId'() {
        given:
        def graph = new MapperGraph()

        when:
        def result = graph.groupCodegen('nonexistent')

        then:
        !result.isPresent()
    }

    def 'realisedSubgraph is read-only (no addNode/addEdge surface)'() {
        expect:
        !RealisedSubgraph.class.methods.any { it.name == 'addNode' }
        !RealisedSubgraph.class.methods.any { it.name == 'addEdge' }
    }

    def 'apply commits all nodes and edges from a delta'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        def loc3 = Mock(Location)
        def loc4 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        loc3.encode() >> 'tgt[c]'
        loc3.segment() >> 'tgt[c]'
        loc4.encode() >> 'tgt[d]'
        loc4.segment() >> 'tgt[d]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def n3 = node(otherScope, loc3, Optional.of(typeMirror))
        def n4 = node(otherScope, loc4, Optional.of(typeMirror))
        def e1 = realisedEdge(n3, n4)
        def e2 = realisedEdge(n1, n2)
        def delta = GraphDelta.of([n1, n2], [e1, e2])

        when:
        graph.apply(delta)

        then:
        graph.nodeCount() == 4
        graph.edgeCount() == 2
        def nodeIds = graph.nodes().collect { it.id() }.toList()
        def expectedIds = [n1.id(), n2.id(), n3.id(), n4.id()].sort()
        nodeIds.sort() == expectedIds
        def edgeKinds = graph.edges().collect { it.kind }.toList()
        edgeKinds == [EdgeKind.REALISED, EdgeKind.REALISED]
    }

    def 'apply with an empty delta is a no-op'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def n = node(scope, loc, Optional.of(typeMirror))
        graph.addNode(n)
        def beforeNodeCount = graph.nodeCount()
        def beforeEdgeCount = graph.edgeCount()

        when:
        graph.apply(GraphDelta.empty())

        then:
        graph.nodeCount() == beforeNodeCount
        graph.edgeCount() == beforeEdgeCount
    }

    def 'apply is idempotent on duplicate deltas'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        def loc3 = Mock(Location)
        def loc4 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        loc3.encode() >> 'tgt[c]'
        loc3.segment() >> 'tgt[c]'
        loc4.encode() >> 'tgt[d]'
        loc4.segment() >> 'tgt[d]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def n3 = node(otherScope, loc3, Optional.of(typeMirror))
        def n4 = node(otherScope, loc4, Optional.of(typeMirror))
        def e1 = realisedEdge(n3, n4)
        def e2 = realisedEdge(n1, n2)
        def delta = GraphDelta.of([n1, n2], [e1, e2])

        when:
        graph.apply(delta)
        def afterFirst = [graph.nodeCount(), graph.edgeCount()]
        graph.apply(delta)
        def afterSecond = [graph.nodeCount(), graph.edgeCount()]

        then:
        afterFirst == afterSecond
    }

    def 'apply commits nodes before edges'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def e1 = realisedEdge(n1, n2)
        def delta = GraphDelta.of([n1, n2], [e1])

        when:
        graph.apply(delta)

        then:
        noExceptionThrown()
    }

    def 'apply commits groupRegistrations from delta'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def e1 = realisedEdge(n1, n2)
        def codegen = Mock(GroupCodegen)
        def registration = new GroupRegistration('g1', codegen)
        def delta = GraphDelta.of([n1, n2], [e1], [registration])

        when:
        graph.apply(delta)

        then:
        graph.nodeCount() == 2
        graph.edgeCount() == 1
        graph.groupCodegen('g1').isPresent()
        graph.groupCodegen('g1').get() == codegen
        graph.groupCodegen('nonexistent').isEmpty()
    }

    def 'apply with duplicate groupRegistrations is idempotent'() {
        given:
        def graph = new MapperGraph()
        def typeMirror = Mock(TypeMirror)
        typeMirror.toString() >> 'Person'
        def loc1 = Mock(Location)
        def loc2 = Mock(Location)
        loc1.encode() >> 'src[a]'
        loc1.segment() >> 'src[a]'
        loc2.encode() >> 'src[b]'
        loc2.segment() >> 'src[b]'
        def n1 = node(scope, loc1, Optional.of(typeMirror))
        def n2 = node(scope, loc2, Optional.of(typeMirror))
        def e1 = realisedEdge(n1, n2)
        def codegen1 = Mock(GroupCodegen)
        def codegen2 = Mock(GroupCodegen)
        def registration1 = new GroupRegistration('g1', codegen1)
        def registration2 = new GroupRegistration('g1', codegen2)
        def delta = GraphDelta.of([n1, n2], [e1], [registration1, registration2])

        when:
        graph.apply(delta)

        then:
        graph.nodeCount() == 2
        graph.edgeCount() == 1
        graph.groupCodegen('g1').isPresent()
        graph.groupCodegen('g1').get() == codegen1
    }
}
