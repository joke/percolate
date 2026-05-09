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
        locA.segment() >> 'src[a]'
        def locB = Mock(Location)
        locB.encode() >> 'src[b]'
        locB.segment() >> 'src[b]'
        def emptyType = Optional.empty()
        def nodeA = new Node(emptyType, locA, scope, Optional.empty())
        def nodeB = new Node(emptyType, locB, scope, Optional.empty())

        when:
        def e1 = new Edge(nodeA, nodeA, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(nodeB, nodeB, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

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
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def toA = Mock(Location)
        toA.encode() >> 'tgt[a]'
        toA.segment() >> 'tgt[a]'
        def toB = Mock(Location)
        toB.encode() >> 'tgt[b]'
        toB.segment() >> 'tgt[b]'
        def nodeToA = new Node(emptyType, toA, scope, Optional.empty())
        def nodeToB = new Node(emptyType, toB, scope, Optional.empty())

        when:
        def e1 = new Edge(node, nodeToA, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(node, nodeToB, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

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
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def e1 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(node, node, 2, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

        then:
        e1.compareTo(e2) < 0
    }

    def 'compareTo orders by kind fourth'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def e1 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

        then:
        e1.compareTo(e2) < 0
    }

    def 'compareTo orders by directive presence fifth'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def mirror = Mock(AnnotationMirror)

        when:
        def e1 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(node, node, 1, EdgeKind.SEED, Optional.of(mirror), Optional.empty(), Optional.empty(), Optional.empty())

        then:
        e1.compareTo(e2) < 0
    }

    def 'compareTo orders by strategyClassFqn sixth'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def e1 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('a'))
        def e2 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('b'))

        then:
        e1.compareTo(e2) < 0
    }

    def 'equals excludes codegen and groupId but includes strategyClassFqn'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def codegen1 = Mock(EdgeCodegen)
        def codegen2 = Mock(EdgeCodegen)

        when:
        // Same strategyClassFqn, different codegen → equal
        def e1 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen1), Optional.of('com.example.A'))
        def e2 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen2), Optional.of('com.example.A'))
        // Different strategyClassFqn → not equal
        def e3 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen1), Optional.of('com.example.B'))
        // Same strategyClassFqn, different groupId → equal
        def e4 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.of('g1'), Optional.empty(), Optional.of('com.example.A'))
        def e5 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.of('g2'), Optional.empty(), Optional.of('com.example.A'))

        then:
        e1.equals(e2)
        e1.hashCode() == e2.hashCode()
        !e1.equals(e3)
        e4.equals(e5)
        e4.hashCode() == e5.hashCode()
    }

    def 'equals includes kind'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def e1 = new Edge(node, node, 1, EdgeKind.SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        def e2 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

        then:
        !e1.equals(e2)
    }

    def 'hashCode excludes codegen and groupId but includes strategyClassFqn'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def codegen1 = Mock(EdgeCodegen)
        def codegen2 = Mock(EdgeCodegen)

        when:
        // Same strategyClassFqn, different codegen → same hashCode
        def e1 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen1), Optional.of('a'))
        def e2 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen2), Optional.of('a'))
        // Different strategyClassFqn → different hashCode (usually)
        def e3 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.of(codegen1), Optional.of('b'))
        // Same strategyClassFqn, different groupId → same hashCode
        def e4 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.of('g1'), Optional.empty(), Optional.of('a'))
        def e5 = new Edge(node, node, 1, EdgeKind.REALISED, Optional.empty(), Optional.of('g2'), Optional.empty(), Optional.of('a'))

        then:
        e1.hashCode() == e2.hashCode()
        e1.hashCode() != e3.hashCode()
        e4.hashCode() == e5.hashCode()
    }

    def 'Edge.seed() produces kind=SEED, sentinel weight, directive present'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def mirror = Mock(AnnotationMirror)

        when:
        def edge = Edge.seed(node, node, mirror)

        then:
        edge.kind == EdgeKind.SEED
        edge.weight == Weights.SENTINEL_UNREALISED
        edge.directive.isPresent()
        edge.directive.get() == mirror
        !edge.groupId.isPresent()
        !edge.codegen.isPresent()
        !edge.strategyClassFqn.isPresent()
    }

    def 'Edge.realised() produces kind=REALISED, codegen and strategyClassFqn present'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())
        def codegen = Mock(EdgeCodegen)

        when:
        def edge = Edge.realised(node, node, Weights.STEP, Optional.of('group1'), codegen, 'com.example.MyStrategy')

        then:
        edge.kind == EdgeKind.REALISED
        edge.weight == Weights.STEP
        !edge.directive.isPresent()
        edge.groupId.isPresent()
        edge.groupId.get() == 'group1'
        edge.codegen.isPresent()
        edge.codegen.get() == codegen
        edge.strategyClassFqn.isPresent()
        edge.strategyClassFqn.get() == 'com.example.MyStrategy'
    }

    def 'Edge.marker() produces kind=MARKER, weight=NOOP, strategyClassFqn present'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def edge = Edge.marker(node, node, 'com.example.MyStrategy')

        then:
        edge.kind == EdgeKind.MARKER
        edge.weight == Weights.NOOP
        !edge.directive.isPresent()
        !edge.groupId.isPresent()
        !edge.codegen.isPresent()
        edge.strategyClassFqn.isPresent()
        edge.strategyClassFqn.get() == 'com.example.MyStrategy'
    }

    def 'Edge.subSeed() produces kind=SUB_SEED, sentinel weight, strategyClassFqn present'() {
        given:
        def scope = Mock(Scope)
        scope.encode() >> 'map()'
        def loc = Mock(Location)
        loc.encode() >> 'src[x]'
        loc.segment() >> 'src[x]'
        def emptyType = Optional.empty()
        def node = new Node(emptyType, loc, scope, Optional.empty())

        when:
        def edge = Edge.subSeed(node, node, 'com.example.MyStrategy', Optional.empty())

        then:
        edge.kind == EdgeKind.SUB_SEED
        edge.weight == Weights.SENTINEL_UNREALISED
        !edge.directive.isPresent()
        !edge.groupId.isPresent()
        !edge.codegen.isPresent()
        edge.strategyClassFqn.isPresent()
        edge.strategyClassFqn.get() == 'com.example.MyStrategy'
    }
}
