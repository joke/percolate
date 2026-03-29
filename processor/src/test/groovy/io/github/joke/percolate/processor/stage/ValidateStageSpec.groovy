package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.DiscoveredMethod
import io.github.joke.percolate.processor.model.GetterAccessor
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ValidateStageSpec extends Specification {

    ValidateStage stage = new ValidateStage()
    TypeMirror typeMirror = Stub()
    TypeElement mapperType = Stub()
    ExecutableElement dummyMethod = Stub()

    def 'succeeds when all targets are mapped'() {
        given:
        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s1 = new SourcePropertyNode('firstName', typeMirror,
                new GetterAccessor('firstName', typeMirror, dummyMethod))
        final t1 = new TargetPropertyNode('givenName', typeMirror,
                new ConstructorParamAccessor('givenName', typeMirror, dummyMethod, 0))
        final s2 = new SourcePropertyNode('lastName', typeMirror,
                new GetterAccessor('lastName', typeMirror, dummyMethod))
        final t2 = new TargetPropertyNode('familyName', typeMirror,
                new ConstructorParamAccessor('familyName', typeMirror, dummyMethod, 1))

        g.addVertex(s1)
        g.addVertex(t1)
        g.addVertex(s2)
        g.addVertex(t2)
        g.addEdge(s1, t1, new MappingEdge(MappingEdge.Type.DIRECT))
        g.addEdge(s2, t2, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = Mock(DiscoveredMethod)
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): g])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
    }

    def 'fails when target property is unmapped with rich error message'() {
        given:
        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s1 = new SourcePropertyNode('firstName', typeMirror,
                new GetterAccessor('firstName', typeMirror, dummyMethod))
        final s2 = new SourcePropertyNode('midleName', typeMirror,
                new GetterAccessor('midleName', typeMirror, dummyMethod))
        final t1 = new TargetPropertyNode('givenName', typeMirror,
                new ConstructorParamAccessor('givenName', typeMirror, dummyMethod, 0))
        final t2 = new TargetPropertyNode('middleName', typeMirror,
                new ConstructorParamAccessor('middleName', typeMirror, dummyMethod, 1))

        g.addVertex(s1)
        g.addVertex(s2)
        g.addVertex(t1)
        g.addVertex(t2)
        g.addEdge(s1, t1, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = Mock(DiscoveredMethod)
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): g])

        when:
        final result = stage.execute(mappingGraph)

        then:
        !result.isSuccess()
        final message = result.errors().find { it.message.contains('middleName') }?.message
        message.contains("Unmapped target property 'middleName'")
        message.contains('Unmapped source properties: [midleName]')
        message.contains("Did you mean to map 'midleName' -> 'middleName'?")
    }

    def 'fails when target has duplicate mappings with rich error message'() {
        given:
        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s1 = new SourcePropertyNode('a', typeMirror,
                new GetterAccessor('a', typeMirror, dummyMethod))
        final s2 = new SourcePropertyNode('b', typeMirror,
                new GetterAccessor('b', typeMirror, dummyMethod))
        final t1 = new TargetPropertyNode('x', typeMirror,
                new ConstructorParamAccessor('x', typeMirror, dummyMethod, 0))

        g.addVertex(s1)
        g.addVertex(s2)
        g.addVertex(t1)
        g.addEdge(s1, t1, new MappingEdge(MappingEdge.Type.DIRECT))
        g.addEdge(s2, t1, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = Mock(DiscoveredMethod)
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): g])

        when:
        final result = stage.execute(mappingGraph)

        then:
        !result.isSuccess()
        final message = result.errors().first().message
        message.contains("Conflicting mappings for target property 'x'")
        message.contains('Mapped from: [a, b]')
    }

    def 'exports graph as DOT format'() {
        given:
        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s1 = new SourcePropertyNode('firstName', typeMirror,
                new GetterAccessor('firstName', typeMirror, dummyMethod))
        final t1 = new TargetPropertyNode('givenName', typeMirror,
                new ConstructorParamAccessor('givenName', typeMirror, dummyMethod, 0))
        g.addVertex(s1)
        g.addVertex(t1)
        g.addEdge(s1, t1, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = Mock(DiscoveredMethod)
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): g])

        expect:
        final dot = stage.exportDot(mappingGraph)
        dot.contains('digraph')
    }
}
