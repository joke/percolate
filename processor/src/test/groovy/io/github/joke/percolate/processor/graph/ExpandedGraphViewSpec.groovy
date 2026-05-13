package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.stream.Collectors

@Tag('unit')
class ExpandedGraphViewSpec extends Specification {

    TypeMirror mockTypeMirror(String typeName) {
        def te = Mock(TypeElement)
        te.qualifiedName >> typeName
        def tm = Mock(DeclaredType)
        tm.asElement() >> te
        tm.kind >> TypeKind.DECLARED
        tm.toString() >> typeName
        tm
    }

    Scope scope(String encode) {
        def s = Mock(Scope)
        s.encode() >> encode
        s
    }

    def 'SEED edges are filtered out of the view'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addEdge(Edge.seed(from, to, mirror))

        when:
        def view = graph.expandedView()
        def edges = view.edges().collect(Collectors.toList())

        then:
        edges.isEmpty()
    }

    def 'MARKER edges are filtered out of the view'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.marker(from, to, 'SomeStrategy'))

        when:
        def view = graph.expandedView()
        def edges = view.edges().collect(Collectors.toList())

        then:
        edges.isEmpty()
    }

    def 'REALISED edges are retained in the view'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def from = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.of(typeMirror), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.realised(from, to, 1, Optional.empty(), (vars, inputs) -> null, 'GetterRead'))

        when:
        def view = graph.expandedView()
        def edges = view.edges().collect(Collectors.toList())

        then:
        edges.size() == 1
        edges[0].kind == EdgeKind.REALISED
    }

    def 'SUB_SEED edges are retained in the view'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def from = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.of(typeMirror), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.subSeed(from, to, 'AutoRecurse', Optional.empty()))

        when:
        def view = graph.expandedView()
        def edges = view.edges().collect(Collectors.toList())

        then:
        edges.size() == 1
        edges[0].kind == EdgeKind.SUB_SEED
    }

    def 'untyped placeholder is hidden when typed counterpart exists at same scope and loc'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'java.lang.String'
        def untyped = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def typed = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        graph.addNode(untyped)
        graph.addNode(typed)

        when:
        def view = graph.expandedView()
        def nodes = view.nodes().collect(Collectors.toList())

        then:
        nodes.size() == 1
        nodes[0] == typed
    }

    def 'untyped placeholder is retained when no typed counterpart exists'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def untyped = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        graph.addNode(untyped)

        when:
        def view = graph.expandedView()
        def nodes = view.nodes().collect(Collectors.toList())

        then:
        nodes.size() == 1
        nodes[0] == untyped
    }

    def 'view construction does not mutate the underlying graph'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addEdge(Edge.seed(from, to, mirror))
        final var originalNodeCount = graph.nodeCount()
        final var originalEdgeCount = graph.edgeCount()

        when:
        graph.expandedView()

        then:
        graph.nodeCount() == originalNodeCount
        graph.edgeCount() == originalEdgeCount
    }

    def 'expandedView accessor returns a non-null view'() {
        given:
        def graph = new MapperGraph()
        def s = scope('map()')
        def node = new Node(Optional.of(mockTypeMirror('String')), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        graph.addNode(node)

        when:
        def view = graph.expandedView()

        then:
        view != null
        view.nodes() != null
        view.edges() != null
        view.nodesByScope(s) != null
    }
}
