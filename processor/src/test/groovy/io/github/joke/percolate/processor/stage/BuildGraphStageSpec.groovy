package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.DiscoveredMethod
import io.github.joke.percolate.processor.model.DiscoveredModel
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MapDirective
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class BuildGraphStageSpec extends Specification {

    BuildGraphStage stage = new BuildGraphStage()

    def 'builds graph with source and target nodes connected by direct edge'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        def targetAccessor = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)

        def method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('firstName', 'givenName')])
        def discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [givenName: targetAccessor])
        def model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        def result = stage.execute(model)

        then:
        result.isSuccess()
        def graph = result.value().graph
        graph.vertexSet().size() == 2
        graph.edgeSet().size() == 1

        and:
        graph.vertexSet().find { it instanceof SourcePropertyNode }.name() == 'firstName'
        graph.vertexSet().find { it instanceof TargetPropertyNode }.name() == 'givenName'
        graph.edgeSet().first().type == MappingEdge.Type.DIRECT
    }

    def 'fails for unknown source property'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def targetAccessor = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)

        def method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('nonexistent', 'givenName')])
        def discovered = new DiscoveredMethod(method,
                [:],
                [givenName: targetAccessor])
        def model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        def result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().any { it.message.contains('Unknown source property: nonexistent') }
    }

    def 'fails for unknown target property'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))

        def method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('firstName', 'nonexistent')])
        def discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [:])
        def model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        def result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().any { it.message.contains('Unknown target property: nonexistent') }
    }

    def 'adds all target properties as nodes even without mappings'() {
        given:
        def typeMirror = Mock(TypeMirror)
        def sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        def targetAccessor1 = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)
        def targetAccessor2 = new ConstructorParamAccessor('familyName', typeMirror, Mock(ExecutableElement), 1)

        def method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('firstName', 'givenName')])
        def discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [givenName: targetAccessor1, familyName: targetAccessor2])
        def model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        def result = stage.execute(model)

        then:
        result.isSuccess()
        def targetNodes = result.value().graph.vertexSet().findAll { it instanceof TargetPropertyNode }
        targetNodes.size() == 2
    }
}
