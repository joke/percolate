package io.github.joke.percolate.processor.transform

import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.BFSShortestPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class TransformResolutionSpec extends Specification {

    def 'exploration graph is preserved when path is null'() {
        given:
        final graph = emptyGraph()
        final sourceNode = new TypeNode(Stub(TypeMirror), 'Source')
        final targetNode = new TypeNode(Stub(TypeMirror), 'Target')
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        final resolution = new TransformResolution(graph, null)

        expect:
        resolution.explorationGraph === graph
        resolution.explorationGraph.vertexSet().size() == 2
        resolution.path == null
    }

    def 'exploration graph is preserved when path is present'() {
        given:
        final result = graphWithPath()
        final resolution = new TransformResolution(result.graph, result.path)

        expect:
        resolution.explorationGraph === result.graph
        resolution.path === result.path
    }

    def 'ResolvedMapping.isResolved() is false when transformResolution is null'() {
        given:
        final mapping = new ResolvedMapping([], 'src', null, 'target', null, null)

        expect:
        !mapping.isResolved()
    }

    def 'ResolvedMapping.isResolved() is false when path is null'() {
        given:
        final resolution = new TransformResolution(emptyGraph(), null)
        final mapping = new ResolvedMapping([], 'src', null, 'target', resolution, null)

        expect:
        !mapping.isResolved()
    }

    def 'ResolvedMapping.isResolved() is true when path exists'() {
        given:
        final result = graphWithPath()
        final resolution = new TransformResolution(result.graph, result.path)
        final mapping = new ResolvedMapping([], 'src', null, 'target', resolution, null)

        expect:
        mapping.isResolved()
    }

    def 'ResolvedMapping.getEdges() returns empty list when transformResolution is null'() {
        given:
        final mapping = new ResolvedMapping([], 'src', null, 'target', null, null)

        expect:
        mapping.edges.isEmpty()
    }

    def 'ResolvedMapping.getEdges() returns empty list when path is null'() {
        given:
        final resolution = new TransformResolution(emptyGraph(), null)
        final mapping = new ResolvedMapping([], 'src', null, 'target', resolution, null)

        expect:
        mapping.edges.isEmpty()
    }

    def 'ResolvedMapping.getEdges() delegates to path edge list'() {
        given:
        final result = graphWithPath()
        final resolution = new TransformResolution(result.graph, result.path)
        final mapping = new ResolvedMapping([], 'src', null, 'target', resolution, null)

        expect:
        mapping.edges == result.path.edgeList
        !mapping.edges.isEmpty()
    }

    private DefaultDirectedGraph<TypeNode, TransformEdge> emptyGraph() {
        return new DefaultDirectedGraph<>(TransformEdge)
    }

    private Map graphWithPath() {
        final sourceNode = new TypeNode(Stub(TypeMirror) { toString() >> 'Source' }, 'Source')
        final targetNode = new TypeNode(Stub(TypeMirror) { toString() >> 'Target' }, 'Target')
        final graph = emptyGraph()
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        final edge = new TransformEdge(new DirectAssignableStrategy(), Stub(TransformProposal))
        edge.resolveTemplate({ input -> input })
        graph.addEdge(sourceNode, targetNode, edge)
        final path = new BFSShortestPath<>(graph).getPath(sourceNode, targetNode)
        return [graph: graph, path: path]
    }
}
