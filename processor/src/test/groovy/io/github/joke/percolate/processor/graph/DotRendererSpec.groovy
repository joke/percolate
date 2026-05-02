package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement

@Tag('unit')
class DotRendererSpec extends Specification {

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

    def 'output is byte-stable across two renderings'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map(Person)')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('person')), s)
        def tgtLoc = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s)
        graph.addNode(srcLoc)
        graph.addNode(tgtLoc)
        def te = typeElement('com.example.StableMapper')

        when:
        def output1 = renderer.render(graph, te)
        def output2 = renderer.render(graph, te)

        then:
        output1 == output2
    }

    def 'per-method clusters are emitted'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s1 = scope('map(Person)')
        def s2 = scope('map(Address)')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'Person'
        def src1 = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('person')), s1)
        def tgt1 = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s1)
        def src2 = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('address')), s2)
        def tgt2 = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s2)
        graph.addNode(src1)
        graph.addNode(tgt1)
        graph.addNode(src2)
        graph.addNode(tgt2)
        def te = typeElement('com.example.ClusterMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('subgraph "cluster_map(Person)"')
        output.contains('subgraph "cluster_map(Address)"')
    }

    def 'vertex order matches Node.id() ascending'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def locZ = new Node(Optional.empty(), new SourceLocation(AccessPath.of('z')), s)
        def locA = new Node(Optional.empty(), new SourceLocation(AccessPath.of('a')), s)
        def locM = new Node(Optional.empty(), new SourceLocation(AccessPath.of('m')), s)
        graph.addNode(locZ)
        graph.addNode(locA)
        graph.addNode(locM)
        def te = typeElement('com.example.OrderedMapper')

        when:
        def output = renderer.render(graph, te)
        def lines = output.split('\\n')
        def idsInOrder = lines.findAll { it.contains('[') && it.contains(']') && it.contains('shape=') }
                .collect { it.replaceAll(/.*"([^"]+)" \[.*/, '$1') }

        then:
        idsInOrder == idsInOrder.sort()
    }

    def 'attribute keys are ordered ascending within a statement'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s)
        graph.addNode(srcLoc)
        def te = typeElement('com.example.AttrOrderMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        def nodeLines = output.split('\\n').findAll { it.contains('shape=') && !it.contains('->') }
        nodeLines.size() > 0
        def line = nodeLines[0]
        line.indexOf('label') < line.indexOf('shape')
    }

    def 'special characters in labels are escaped'() {
        expect:
        DotRenderer.escapeDot('hello "world"') == 'hello \\"world\\"'
        DotRenderer.escapeDot('back\\\\slash') == 'back\\\\\\\\slash'
        DotRenderer.escapeDot('line\\nbreak') == 'line\\\\nbreak'
        DotRenderer.escapeDot('<tag>') == '\\<tag\\>'
    }

    def 'source nodes render as box'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s)
        graph.addNode(srcLoc)
        def te = typeElement('com.example.BoxMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('shape="box"')
    }

    def 'target nodes render with oval shape'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def tgtLoc = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s)
        graph.addNode(tgtLoc)
        def te = typeElement('com.example.OvalMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('shape="oval"')
    }

    def 'directive-seeded edge is marked with bold style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s)
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s)
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 1, Optional.of(mirror)))
        def te = typeElement('com.example.BoldMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="bold"')
    }

    def 'output ends with a single trailing newline'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def te = typeElement('com.example.TrailingMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.endsWith('\n')
        !output.endsWith('\n\n')
    }

    def 'digraph name uses mapper FQN'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def te = typeElement('com.example.FQNMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('digraph "com.example.FQNMapper"')
    }

    def 'empty graph renders minimal valid DOT'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def te = typeElement('com.example.EmptyMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('digraph "com.example.EmptyMapper"')
        output.contains('{')
        output.contains('}')
    }
}
