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

    def 'builds per-method graph with source and target nodes connected by direct edge'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)

        final method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('firstName', 'givenName')])
        final discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [givenName: targetAccessor])
        final model = new DiscoveredModel(Mock(TypeElement), [discovered])

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[discovered]
        graph.vertexSet().size() == 2
        graph.edgeSet().size() == 1
        graph.vertexSet().find { it instanceof SourcePropertyNode }.name == 'firstName'
        graph.vertexSet().find { it instanceof TargetPropertyNode }.name == 'givenName'
        graph.edgeSet().first().type == MappingEdge.Type.DIRECT
    }

    def 'fails for unknown source property with rich error message'() {
        given:
        final typeMirror = Stub(TypeMirror) { toString() >> 'com.example.Source' }
        final executableElement = Stub(ExecutableElement) { toString() >> 'toTarget(Source)' }
        final targetAccessor = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)

        final method = new MappingMethodModel(
                executableElement, typeMirror, typeMirror,
                [new MapDirective('nonexistent', 'givenName')])
        final discovered = new DiscoveredMethod(method,
                [:],
                [givenName: targetAccessor])
        final model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final message = result.errors().first().message
        message.contains("Unknown source property 'nonexistent'")
        message.contains('toTarget(Source)')
        message.contains('Source type: com.example.Source')
    }

    def 'fails for unknown target property with rich error message'() {
        given:
        final typeMirror = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final executableElement = Stub(ExecutableElement) { toString() >> 'toTarget(Source)' }
        final sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))

        final method = new MappingMethodModel(
                executableElement, typeMirror, typeMirror,
                [new MapDirective('firstName', 'nonexistent')])
        final discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [:])
        final model = new DiscoveredModel(Mock(TypeElement), [discovered])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final message = result.errors().first().message
        message.contains("Unknown target property 'nonexistent'")
        message.contains('toTarget(Source)')
        message.contains('Target type: com.example.Target')
    }

    def 'adds all target properties as nodes even without mappings'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final sourceAccessor = new GetterAccessor('firstName', typeMirror, Mock(ExecutableElement))
        final targetAccessor1 = new ConstructorParamAccessor('givenName', typeMirror, Mock(ExecutableElement), 0)
        final targetAccessor2 = new ConstructorParamAccessor('familyName', typeMirror, Mock(ExecutableElement), 1)

        final method = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('firstName', 'givenName')])
        final discovered = new DiscoveredMethod(method,
                [firstName: sourceAccessor],
                [givenName: targetAccessor1, familyName: targetAccessor2])
        final model = new DiscoveredModel(Mock(TypeElement), [discovered])

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final targetNodes = result.value().methodGraphs[discovered].vertexSet().findAll { it instanceof TargetPropertyNode }
        targetNodes.size() == 2
    }

    def 'creates isolated graphs per method'() {
        given:
        final typeMirror = Mock(TypeMirror)
        final sourceAccessor1 = new GetterAccessor('a', typeMirror, Mock(ExecutableElement))
        final targetAccessor1 = new ConstructorParamAccessor('b', typeMirror, Mock(ExecutableElement), 0)
        final sourceAccessor2 = new GetterAccessor('x', typeMirror, Mock(ExecutableElement))
        final targetAccessor2 = new ConstructorParamAccessor('y', typeMirror, Mock(ExecutableElement), 0)

        final method1 = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('a', 'b')])
        final discovered1 = new DiscoveredMethod(method1, [a: sourceAccessor1], [b: targetAccessor1])

        final method2 = new MappingMethodModel(
                Mock(ExecutableElement), typeMirror, typeMirror,
                [new MapDirective('x', 'y')])
        final discovered2 = new DiscoveredMethod(method2, [x: sourceAccessor2], [y: targetAccessor2])

        final model = new DiscoveredModel(Mock(TypeElement), [discovered1, discovered2])

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        result.value().methodGraphs.size() == 2
        result.value().methodGraphs[discovered1].vertexSet().size() == 2
        result.value().methodGraphs[discovered2].vertexSet().size() == 2
        result.value().methodGraphs[discovered1].vertexSet().every { it.name in ['a', 'b'] }
        result.value().methodGraphs[discovered2].vertexSet().every { it.name in ['x', 'y'] }
    }
}
