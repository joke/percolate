package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.Optional

@Tag('unit')
class DotRendererExpandedSpec extends Specification {

    TypeElement typeElement(String fqn) {
        def te = Mock(TypeElement)
        def name = Mock(Name)
        name.toString() >> fqn
        te.getQualifiedName() >> name
        te
    }

    Scope scope(String encode) {
        def s = Mock(Scope)
        s.encode() >> encode
        s
    }

    def 'REALISED edge renders with dashed style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('GetterRead')))
        def te = typeElement('com.example.RealisedMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="dashed"')
        output.contains('REALISED')
    }

    def 'MARKER edge renders with dotted style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 0, EdgeKind.MARKER, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('GetterRead')))
        def te = typeElement('com.example.MarkerMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="dotted"')
        output.contains('MARKER')
    }

    def 'SUB_SEED edge renders with bold style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, Weights.SENTINEL_UNREALISED, EdgeKind.SUB_SEED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('AutoRecurse')))
        def te = typeElement('com.example.SubSeedMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="bold"')
        output.contains('SUB_SEED')
    }

    def 'groupId appears in edge attributes when present'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.realised(from, to, 1, Optional.of('g1'), (vars, inputs) -> null, 'ConstructorCall'))
        def te = typeElement('com.example.GroupMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('g1')
    }

    def 'edges share groupId when from same group'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from1 = new Node(Optional.empty(), new SourceLocation(AccessPath.of('firstName')), s, Optional.empty())
        def from2 = new Node(Optional.empty(), new SourceLocation(AccessPath.of('lastName')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s, Optional.empty())
        graph.addNode(from1)
        graph.addNode(from2)
        graph.addNode(to)
        graph.addEdge(Edge.realised(from1, to, 1, Optional.of('g1'), (vars, inputs) -> null, 'ConstructorCall'))
        graph.addEdge(Edge.realised(from2, to, 1, Optional.of('g1'), (vars, inputs) -> null, 'ConstructorCall'))
        def te = typeElement('com.example.SharedGroupMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        def g1Count = output.findAll(/g1/).size()
        g1Count >= 2
    }

    def 'REALISED edge with strategyClassFqn shows it'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.realised(from, to, 1, Optional.empty(), (vars, inputs) -> null, 'io.github.joke.percolate.processor.spi.builtins.GetterRead'))
        def te = typeElement('com.example.StrategyFQNMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('GetterRead')
    }

    def 'all four edge kinds can coexist in same graph'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def seedFrom = new Node(Optional.empty(), new SourceLocation(AccessPath.of('a')), s, Optional.empty())
        def seedTo = new Node(Optional.empty(), new TargetLocation(TargetPath.of('b')), s, Optional.empty())
        def realisedFrom = new Node(Optional.of(mockTypeMirror('String')), new SourceLocation(AccessPath.of('c')), s, Optional.empty())
        def realisedTo = new Node(Optional.of(mockTypeMirror('String')), new TargetLocation(TargetPath.of('d')), s, Optional.empty())
        graph.addNode(seedFrom)
        graph.addNode(seedTo)
        graph.addNode(realisedFrom)
        graph.addNode(realisedTo)
        def mirror = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(seedFrom, seedTo, mirror))
        graph.addEdge(Edge.realised(realisedFrom, realisedTo, 1, Optional.empty(), (vars, inputs) -> null, 'DirectAssign'))
        graph.addEdge(Edge.marker(seedFrom, realisedFrom, 'GetterRead'))
        graph.addEdge(Edge.subSeed(seedTo, realisedTo, 'AutoRecurse', Optional.empty()))
        def te = typeElement('com.example.AllKindsMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('SEED')
        output.contains('REALISED')
        output.contains('MARKER')
        output.contains('SUB_SEED')
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
