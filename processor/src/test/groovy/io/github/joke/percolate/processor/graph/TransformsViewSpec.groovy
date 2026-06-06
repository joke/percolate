package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TransformsViewSpec extends Specification {

    def 'transformsView accessor returns a non-null view'() {
        given:
        final var graph = buildGraphWithRealisedEdge()

        when:
        final var view = graph.transformsView()

        then:
        view != null
        view.nodes() != null
        view.edges() != null
        view.nodesByScope(HarnessScope.of('test')) != null
    }

    def 'Only REALISED edges pass the edge mask'() {
        given:
        final var graph = buildGraphWithMixedKinds()

        when:
        final var edges = graph.transformsView().edges().toList()

        then:
        edges.every { it.kind == EdgeKind.REALISED }
        edges.size() == 1
    }

    def 'Only nodes incident on REALISED edges pass the vertex mask'() {
        given:
        final var graph = buildGraphWithSubSeedOnlyNode()
        final var realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        final var view = graph.transformsView()

        when:
        final var nodes = view.nodes().toList()

        then:
        nodes.contains(graph.getEdgeSource(realisedEdges[0]))
        nodes.contains(graph.getEdgeTarget(realisedEdges[0]))
        nodes.size() == 2
    }

    def 'Dead-end transformations are retained'() {
        given:
        final var graph = buildGraphWithDeadEndRealised()
        final var view = graph.transformsView()

        when:
        final var edges = view.edges().toList()
        final var nodes = view.nodes().toList()

        then:
        edges.size() == 1
        edges[0].kind == EdgeKind.REALISED
        nodes.size() == 2
    }

    def 'View construction does not mutate the underlying graph'() {
        given:
        final var graph = buildGraphWithRealisedEdge()
        final var nodeCountBefore = graph.nodeCount()
        final var edgeCountBefore = graph.edgeCount()

        when:
        graph.transformsView()

        then:
        graph.nodeCount() == nodeCountBefore
        graph.edgeCount() == edgeCountBefore
    }

    private static MapperGraph buildGraphWithRealisedEdge() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope) 
        final b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope) 
        graph.addNode(a)
        graph.addNode(b)
        final realised = Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(a, b, realised)
        graph
    }

    private static MapperGraph buildGraphWithMixedKinds() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope) 
        final b = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('b')), scope) 
        final c = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('c')), scope) 
        final d = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('d')), scope) 
        graph.addNode(a)
        graph.addNode(b)
        graph.addNode(c)
        graph.addNode(d)
        graph.addEdge(a, b, Edge.seedForTest())
        final realised = Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(c, d, realised)
        graph.addEdge(a, c, Edge.seed(Optional.empty()))
        graph
    }

    private static MapperGraph buildGraphWithSubSeedOnlyNode() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope) 
        final b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope) 
        final c = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('c')), scope) 
        graph.addNode(a)
        graph.addNode(b)
        graph.addNode(c)
        final realised = Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(a, b, realised)
        graph.addEdge(c, a, Edge.seed(Optional.empty()))
        graph
    }

    private static MapperGraph buildGraphWithDeadEndRealised() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final x = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('x')), scope) 
        final y = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('y')), scope) 
        graph.addNode(x)
        graph.addNode(y)
        final xToY = Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(x, y, xToY)
        graph
    }

    private static final class HarnessScope implements Scope {
        private final String name
        static HarnessScope of(final String name) { new HarnessScope(name) }
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}
