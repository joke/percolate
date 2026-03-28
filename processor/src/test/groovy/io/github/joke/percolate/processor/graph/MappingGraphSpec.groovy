package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class MappingGraphSpec extends Specification {

    def 'graph contains source and target nodes connected by direct mapping edge'() {
        given:
        final graph = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final typeMirror = Mock(TypeMirror)
        final sourceNode = new SourcePropertyNode('firstName', typeMirror,
                new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement)))
        final targetNode = new TargetPropertyNode('givenName', typeMirror,
                new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0))

        when:
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        graph.addEdge(sourceNode, targetNode, new MappingEdge(MappingEdge.Type.DIRECT))

        then:
        graph.vertexSet().size() == 2
        graph.edgeSet().size() == 1
        graph.getEdge(sourceNode, targetNode).type == MappingEdge.Type.DIRECT
    }

    def 'PropertyNode toString includes class name and property name'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final node = new SourcePropertyNode('firstName', typeMirror,
                new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement)))

        expect:
        node.toString() == 'SourcePropertyNode(firstName)'
    }
}
