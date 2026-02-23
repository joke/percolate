package io.github.joke.percolate.graph

import io.github.joke.percolate.graph.edge.ConversionEdge
import io.github.joke.percolate.graph.edge.GraphEdge
import io.github.joke.percolate.graph.node.GraphNode
import io.github.joke.percolate.graph.node.TypeNode
import io.github.joke.percolate.spi.ConversionProvider
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.DirectedWeightedMultigraph
import spock.lang.Specification

import javax.lang.model.type.TypeMirror

class LazyMappingGraphSpec extends Specification {

    def "lazily expands conversion edges during outgoingEdgesOf"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def targetType = Mock(TypeMirror) { toString() >> 'TargetType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        base.addVertex(sourceNode)

        def edge = new ConversionEdge(
            ConversionEdge.Kind.MAPPER_METHOD, sourceType, targetType, 'this.convert($expr)')
        def conversion = new ConversionProvider.Conversion(targetType, edge)
        def provider = Mock(ConversionProvider) {
            possibleConversions(sourceType, _) >> [conversion]
        }

        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        def edges = lazy.outgoingEdgesOf(sourceNode)

        then:
        edges.size() == 1
        edges.first() instanceof ConversionEdge
    }

    def "caches expansion — second call returns same edges without re-expanding"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def sourceType = Mock(TypeMirror) { toString() >> 'SourceType' }
        def targetType = Mock(TypeMirror) { toString() >> 'TargetType' }
        def sourceNode = new TypeNode(sourceType, 'source')
        base.addVertex(sourceNode)

        def edge = new ConversionEdge(
            ConversionEdge.Kind.SUBTYPE, sourceType, targetType, '$expr')
        def conversion = new ConversionProvider.Conversion(targetType, edge)
        def provider = Mock(ConversionProvider)

        def lazy = new LazyMappingGraph(base, [provider], null, 5)

        when:
        lazy.outgoingEdgesOf(sourceNode)
        lazy.outgoingEdgesOf(sourceNode)

        then:
        1 * provider.possibleConversions(sourceType, _) >> [conversion]
    }

    def "respects depth limit"() {
        given:
        def base = new DirectedWeightedMultigraph<GraphNode, GraphEdge>(GraphEdge)
        def typeA = Mock(TypeMirror) { toString() >> 'A' }
        def nodeA = new TypeNode(typeA, 'a')
        base.addVertex(nodeA)

        // Provider that always creates a new conversion (infinite chain)
        def provider = Mock(ConversionProvider) {
            possibleConversions(_, _) >> { TypeMirror src, _ ->
                def tgt = Mock(TypeMirror) { toString() >> src.toString() + '+' }
                def e = new ConversionEdge(ConversionEdge.Kind.SUBTYPE, src, tgt, '$expr')
                [new ConversionProvider.Conversion(tgt, e)]
            }
        }

        def lazy = new LazyMappingGraph(base, [provider], null, 3)

        when: 'traverse the graph through BFS'
        def inspector = new ConnectivityInspector(lazy)
        def connected = inspector.connectedSetOf(nodeA)

        then: 'depth is limited'
        connected.size() <= 4 // nodeA + at most 3 expansions
    }
}
