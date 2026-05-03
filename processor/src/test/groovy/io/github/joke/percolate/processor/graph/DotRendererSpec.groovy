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
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('person')), s, Optional.empty())
        def tgtLoc = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s, Optional.empty())
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
        def src1 = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('person')), s1, Optional.empty())
        def tgt1 = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s1, Optional.empty())
        def src2 = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('address')), s2, Optional.empty())
        def tgt2 = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s2, Optional.empty())
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
        def locZ = new Node(Optional.empty(), new SourceLocation(AccessPath.of('z')), s, Optional.empty())
        def locA = new Node(Optional.empty(), new SourceLocation(AccessPath.of('a')), s, Optional.empty())
        def locM = new Node(Optional.empty(), new SourceLocation(AccessPath.of('m')), s, Optional.empty())
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
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
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
        def srcLoc = new Node(Optional.of(typeMirror), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
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
        def tgtLoc = new Node(Optional.empty(), new TargetLocation(TargetPath.of('')), s, Optional.empty())
        graph.addNode(tgtLoc)
        def te = typeElement('com.example.OvalMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('shape="oval"')
    }

    def 'phantom nodes render with diamond shape'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map(Foo)')
        def parentLoc = Mock(Location)
        parentLoc.encode() >> 'src[input]'
        parentLoc.segment() >> 'src[input]'
        def parentType = Mock(javax.lang.model.type.TypeMirror)
        parentType.toString() >> 'Foo'
        def parent = new Node(Optional.of(parentType), parentLoc, s, Optional.empty())
        parent.id() >> 'map(Foo)::src[input]::Foo'
        def phantom = new Node(Optional.empty(), new ElementLocation(), s, Optional.of(parent))
        graph.addNode(parent)
        graph.addNode(phantom)
        def te = typeElement('com.example.DiamondMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('shape="diamond"')
    }

    def 'phantom node renders inside parent cluster'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map(Foo)')
        def parentLoc = Mock(Location)
        parentLoc.encode() >> 'src[input]'
        parentLoc.segment() >> 'src[input]'
        def parentType = Mock(javax.lang.model.type.TypeMirror)
        parentType.toString() >> 'Foo'
        def parent = new Node(Optional.of(parentType), parentLoc, s, Optional.empty())
        parent.id() >> 'map(Foo)::src[input]::Foo'
        def phantom = new Node(Optional.empty(), new ElementLocation(), s, Optional.of(parent))
        graph.addNode(parent)
        graph.addNode(phantom)
        def te = typeElement('com.example.ClusterPhantomMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        // Phantom should appear inside the cluster_ subgraph
        def clusterBlock = output.substring(output.indexOf('cluster_map(Foo)'), output.indexOf('}', output.indexOf('cluster_map(Foo)')))
        clusterBlock.contains(phantom.id())
    }

    def 'phantom without parent throws'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def phantom = new Node(Optional.empty(), new ElementLocation(), s, Optional.empty())
        graph.addNode(phantom)
        def te = typeElement('com.example.OrphanPhantomMapper')

        when:
        renderer.render(graph, te)

        then:
        thrown(Exception)
    }

    def 'sentinel weight renders as infinity'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.seed(from, to, mirror))
        def te = typeElement('com.example.InfinityMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('\u221E')
    }

    def 'numeric weight renders as number'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        def te = typeElement('com.example.NumericMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('REALISED')
        output.contains('1')
    }

    def 'strategyClassFqn appears in edge label when present'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('com.example.GetterReadStrategy')))
        def te = typeElement('com.example.FQNMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('GetterReadStrategy')
    }

    def 'EdgeKind marker appears in edge label'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.seed(from, to, mirror))
        def te = typeElement('com.example.KindMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('SEED')
    }

    def 'SEED edge has solid style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        def mirror = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.seed(from, to, mirror))
        def te = typeElement('com.example.SolidMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="solid"')
    }

    def 'REALISED edge has dashed style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 1, EdgeKind.REALISED, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        def te = typeElement('com.example.DashedMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="dashed"')
    }

    def 'MARKER edge has dotted style'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(new Edge(from, to, 0, EdgeKind.MARKER, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of('com.example.Strategy')))
        def te = typeElement('com.example.DottedMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        output.contains('style="dotted"')
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

    def 'codegen closure never appears in output'() {
        given:
        def renderer = new DotRenderer()
        def graph = new MapperGraph()
        def s = scope('map()')
        def from = new Node(Optional.empty(), new SourceLocation(AccessPath.of('x')), s, Optional.empty())
        def to = new Node(Optional.empty(), new TargetLocation(TargetPath.of('y')), s, Optional.empty())
        def codegen = Mock(EdgeCodegen)
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(Edge.realised(from, to, Weights.STEP, Optional.empty(), codegen, 'com.example.Strategy'))
        def te = typeElement('com.example.NoCodegenMapper')

        when:
        def output = renderer.render(graph, te)

        then:
        !output.contains('lambda')
        !output.contains('$')
        !output.contains('EdgeCodegen')
    }
}
