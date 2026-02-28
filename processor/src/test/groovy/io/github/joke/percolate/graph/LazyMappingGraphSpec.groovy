package io.github.joke.percolate.graph

import io.github.joke.percolate.graph.edge.ConversionEdge
import io.github.joke.percolate.graph.edge.GraphEdge
import io.github.joke.percolate.graph.node.GraphNode
import io.github.joke.percolate.graph.node.TypeNode
import io.github.joke.percolate.spi.ConversionProvider
import org.jgrapht.graph.DirectedWeightedMultigraph
import spock.lang.Specification

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.type.TypeMirror

class LazyMappingGraphSpec extends Specification {

    def "outgoingEdgesOf returns base graph edges — expansion is now handled by WiringStage"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def targetType = Mock(TypeMirror) { toString() >> 'TargetType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        def targetNode = new TypeNode(targetType, 'target')
        base.addVertex(sourceNode)
        base.addVertex(targetNode)
        def edge = new ConversionEdge(ConversionEdge.Kind.MAPPER_METHOD, sourceType, targetType, 'this.convert($expr)')
        base.addEdge(sourceNode, targetNode, edge)

        def provider = Mock(ConversionProvider)
        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        def edges = lazy.outgoingEdgesOf(sourceNode)

        then:
        edges.size() == 1
        edges.first() instanceof ConversionEdge
        0 * provider.canHandle(_, _, _)
    }

    def "caches expansion — second call returns same edges without invoking providers"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        base.addVertex(sourceNode)

        def provider = Mock(ConversionProvider)
        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        lazy.outgoingEdgesOf(sourceNode)
        lazy.outgoingEdgesOf(sourceNode)

        then:
        0 * provider.canHandle(_, _, _)
        0 * provider.provide(_, _, _, _)
    }

    def "respects depth limit — no provider calls even with multiple vertices"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def typeA = Mock(TypeMirror) { toString() >> 'A' }
        def nodeA = new TypeNode(typeA, 'a')
        base.addVertex(nodeA)

        def provider = Mock(ConversionProvider)
        def lazy = new LazyMappingGraph(base, [provider], null, 3)

        when:
        lazy.outgoingEdgesOf(nodeA)

        then:
        0 * provider.canHandle(_, _, _)
    }
}
