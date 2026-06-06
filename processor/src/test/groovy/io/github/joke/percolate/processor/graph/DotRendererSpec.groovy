package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.test.TypeUniverse
import org.jgrapht.Graph
import org.jgrapht.graph.DirectedMultigraph
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class DotRendererSpec extends Specification {

    def 'renders REALISED and SEED edge labels; emits no cluster subgraphs'() {
        given:
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def tgt = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        def edges = [
                [src, tgt, Edge.seedForTest()],
                [src, tgt, Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')],
        ]
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([src, tgt], edges), scope.encode())

        then:
        dot.contains('SEED')
        !dot.contains('MARKER')
        !dot.contains('cluster_')
        !dot.contains('SUB_SEED')
        !dot.contains('ELEMENT_SEED')
    }

    def 'captions the graph with the scope description'() {
        given:
        def scope = new HarnessScope('mapHuman(Person)')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([src], []), scope.encode())

        then:
        dot.contains('label="mapHuman(Person)"')
    }

    def 'renders all nodes as filled boxes coloured by location role; no diamond or oval'() {
        given:
        def scope = new HarnessScope('m()')
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def target = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        def parent = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('c')), scope)
        def element = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('element'), scope, Optional.of(parent))
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([source, target, parent, element], []), scope.encode())

        then:
        // uniform box + filled, no shape-based distinction
        dot.contains('shape="box"')
        dot.contains('style="filled"')
        !dot.contains('shape="oval"')
        !dot.contains('shape="diamond"')
        // three distinct fill colours, one per role
        def fills = (dot =~ /fillcolor="([^"]+)"/).collect { it[1] } as Set
        fills.size() == 3
    }

    def 'REALISED edges dominate (black, elevated penwidth); SEED edges recede to grey'() {
        given:
        def scope = new HarnessScope('m()')
        def a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope)
        def b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope)
        def realised = Edge.realised(2, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'io.github.joke.percolate.spi.builtins.IterableUnwrap')
        def seed = Edge.seedForTest()
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([a, b], [[a, b, realised], [a, b, seed]]), scope.encode())

        then:
        // realised label: simple strategy name + weight, no package prefix
        dot.contains('IterableUnwrap (2)')
        !dot.contains('io.github.joke.percolate.spi.builtins')
        // realised styling
        dot.contains('penwidth="2.0"')
        dot.contains('color="black"')
        // seed receding to grey (line + label)
        dot.contains('color="grey60"')
        dot.contains('fontcolor="grey60"')
    }

    def 'sentinel weight renders as infinity in REALISED labels'() {
        given:
        def scope = new HarnessScope('m()')
        def a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope)
        def b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope)
        def realised = Edge.realised(Weights.SENTINEL_UNREALISED, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([a, b], [[a, b, realised]]), scope.encode())

        then:
        dot.contains('∞')
    }

    def 'node label is the two-line location and simplified type; java.lang stripped'() {
        given:
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([src], []), scope.encode())

        then:
        // backslash-n in the DOT value renders as a Graphviz line break; java.lang.String -> String
        dot.contains('src[in]\\nString')
    }

    def 'double quotes in a label are escaped by the exporter'() {
        given:
        def scope = new HarnessScope('say "hi"')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def renderer = new DotRenderer()

        when:
        def dot = renderer.render(sliceOf([src], []), scope.encode())

        then:
        dot.contains('say \\"hi\\"')
    }

    private static Graph<Node, Edge> sliceOf(Collection<Node> nodes, Collection<List> edges) {
        final Graph<Node, Edge> g = new DirectedMultigraph<>(Edge)
        nodes.each { g.addVertex(it) }
        edges.each { triple ->
            g.addVertex(triple[0])
            g.addVertex(triple[1])
            g.addEdge(triple[0], triple[1], triple[2])
        }
        g
    }
}
