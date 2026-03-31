package io.github.joke.percolate.processor.graph

import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MappingGraphSpec extends Specification {

    def 'graph accepts source and target property nodes connected by mapping edge'() {
        given:
        final graph = new DefaultDirectedGraph<>(Object)
        final sourceNode = new SourcePropertyNode('firstName')
        final targetNode = new TargetPropertyNode('givenName')

        when:
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        graph.addEdge(sourceNode, targetNode, new MappingEdge())

        then:
        graph.vertexSet().size() == 2
        graph.edgeSet().size() == 1
        graph.getEdge(sourceNode, targetNode) instanceof MappingEdge
    }

    def 'graph accepts source root and source property nodes connected by access edge'() {
        given:
        final graph = new DefaultDirectedGraph<>(Object)
        final root = new SourceRootNode('src')
        final prop = new SourcePropertyNode('address')

        when:
        graph.addVertex(root)
        graph.addVertex(prop)
        graph.addEdge(root, prop, new AccessEdge())

        then:
        graph.edgeSet().first() instanceof AccessEdge
    }

    def 'PropertyNode toString includes class name and property name'() {
        expect:
        new SourcePropertyNode('firstName').toString() == 'SourcePropertyNode(firstName)'
        new TargetPropertyNode('lastName').toString() == 'TargetPropertyNode(lastName)'
        new SourceRootNode('source').toString() == 'SourceRootNode(source)'
    }
}
