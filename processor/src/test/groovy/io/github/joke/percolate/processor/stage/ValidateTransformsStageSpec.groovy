package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.DiscoveredMethod
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.transform.ResolvedMapping
import io.github.joke.percolate.processor.transform.ResolvedModel
import org.jgrapht.alg.shortestpath.BFSShortestPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ValidateTransformsStageSpec extends Specification {

    ValidateTransformsStage stage = new ValidateTransformsStage()

    def 'succeeds when all transforms are resolved'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final sourceAccessor = new GetterAccessor('name', sourceType, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('name', targetType, Mock(ExecutableElement), 0)

        final s = new SourcePropertyNode('name', sourceType, sourceAccessor)
        final t = new TargetPropertyNode('name', targetType, targetAccessor)

        final path = createSingleEdgePath(sourceType, targetType)
        final mapping = new ResolvedMapping(s, t, path)

        final execElement = Stub(ExecutableElement) { toString() >> 'map(Source)' }
        final method = new MappingMethodModel(execElement, sourceType, targetType, [])
        final discovered = new DiscoveredMethod(method, [:], [:])
        final model = new ResolvedModel(Mock(TypeElement), [discovered], [(discovered): [mapping]])

        expect:
        final result = stage.execute(model)
        result.isSuccess()
    }

    def 'fails when transform is unresolved'() {
        given:
        final fooType = Stub(TypeMirror) { toString() >> 'com.example.Foo' }
        final barType = Stub(TypeMirror) { toString() >> 'com.example.Bar' }
        final sourceAccessor = new GetterAccessor('data', fooType, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('data', barType, Mock(ExecutableElement), 0)

        final s = new SourcePropertyNode('data', fooType, sourceAccessor)
        final t = new TargetPropertyNode('data', barType, targetAccessor)
        final mapping = new ResolvedMapping(s, t, null)

        final mapperType = Stub(TypeElement) { toString() >> 'TestMapper' }
        final execElement = Stub(ExecutableElement) { toString() >> 'map(Foo)' }
        final method = new MappingMethodModel(execElement, fooType, barType, [])
        final discovered = new DiscoveredMethod(method, [:], [:])
        final model = new ResolvedModel(mapperType, [discovered], [(discovered): [mapping]])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final message = result.errors().first().message
        message.contains("'data'")
        message.contains('com.example.Foo')
        message.contains('com.example.Bar')
        message.contains('map(Foo)')
        message.contains('TestMapper')
    }

    def 'produces one error per unresolved mapping'() {
        given:
        final fooType = Stub(TypeMirror) { toString() >> 'Foo' }
        final barType = Stub(TypeMirror) { toString() >> 'Bar' }
        final bazType = Stub(TypeMirror) { toString() >> 'Baz' }

        final s1 = new SourcePropertyNode('a', fooType, new GetterAccessor('a', fooType, Mock(ExecutableElement)))
        final t1 = new TargetPropertyNode('a', barType, new ConstructorParamAccessor('a', barType, Mock(ExecutableElement), 0))
        final s2 = new SourcePropertyNode('b', fooType, new GetterAccessor('b', fooType, Mock(ExecutableElement)))
        final t2 = new TargetPropertyNode('b', bazType, new ConstructorParamAccessor('b', bazType, Mock(ExecutableElement), 1))

        final mapping1 = new ResolvedMapping(s1, t1, null)
        final mapping2 = new ResolvedMapping(s2, t2, null)

        final execElement = Stub(ExecutableElement) { toString() >> 'map(Foo)' }
        final method = new MappingMethodModel(execElement, fooType, barType, [])
        final discovered = new DiscoveredMethod(method, [:], [:])
        final model = new ResolvedModel(Stub(TypeElement), [discovered], [(discovered): [mapping1, mapping2]])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().size() == 2
    }

    private static createSingleEdgePath(final TypeMirror sourceType, final TypeMirror targetType) {
        final graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        final sourceNode = new TypeNode(sourceType, sourceType.toString())
        final targetNode = new TypeNode(targetType, targetType.toString())
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        graph.addEdge(sourceNode, targetNode, new TransformEdge(new DirectAssignableStrategy(), { input -> input }))
        return new BFSShortestPath<>(graph).getPath(sourceNode, targetNode)
    }
}
