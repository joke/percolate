package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.transform.AccessResolutionFailure
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
        final getter = new GetterAccessor('name', sourceType, Mock(ExecutableElement))
        final writer = new ConstructorParamAccessor('name', targetType, Mock(ExecutableElement), 0)

        final mapping = new ResolvedMapping([getter], 'name', writer, 'name', singleEdgePath(sourceType, targetType), null)
        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final model = new ResolvedModel(Mock(TypeElement), [method], [(method): [mapping]], [:], [:])

        expect:
        stage.execute(model).isSuccess()
    }

    def 'fails with source chain error when source property cannot be resolved'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.Source' }
        final targetType = Mock(TypeMirror)
        final exec = Stub(ExecutableElement) { toString() >> 'map(Source)' }
        final failure = new AccessResolutionFailure('unknown', 0, 'unknown', sourceType, ['name'] as Set)

        final mapping = new ResolvedMapping([], 'unknown', null, 'name', null, failure)
        final method = new MappingMethodModel(exec, sourceType, targetType, [])
        final model = new ResolvedModel(Mock(TypeElement), [method], [(method): [mapping]], [:], [:])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final msg = result.errors().first().message
        msg.contains("Unknown source property 'unknown'")
        msg.contains('map(Source)')
    }

    def 'fails with target error when target accessor cannot be resolved'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final exec = Stub(ExecutableElement) { toString() >> 'map(Source)' }
        final getter = new GetterAccessor('name', sourceType, Mock(ExecutableElement))
        final failure = new AccessResolutionFailure('missing', 0, 'missing', targetType, ['other'] as Set)

        final mapping = new ResolvedMapping([getter], 'name', null, 'missing', null, failure)
        final method = new MappingMethodModel(exec, sourceType, targetType, [])
        final model = new ResolvedModel(Mock(TypeElement), [method], [(method): [mapping]], [:], [:])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final msg = result.errors().first().message
        msg.contains("Unknown target property 'missing'")
        msg.contains('map(Source)')
    }

    def 'fails with unresolved transform error when type transform path is missing'() {
        given:
        final fooType = Stub(TypeMirror) { toString() >> 'com.example.Foo' }
        final barType = Stub(TypeMirror) { toString() >> 'com.example.Bar' }
        final exec = Stub(ExecutableElement) { toString() >> 'map(Foo)' }
        final mapperType = Stub(TypeElement) { toString() >> 'TestMapper' }
        final getter = new GetterAccessor('data', fooType, Mock(ExecutableElement))
        final writer = new ConstructorParamAccessor('data', barType, Mock(ExecutableElement), 0)

        final mapping = new ResolvedMapping([getter], 'data', writer, 'data', null, null)
        final method = new MappingMethodModel(exec, fooType, barType, [])
        final model = new ResolvedModel(mapperType, [method], [(method): [mapping]], [:], [:])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        final msg = result.errors().first().message
        msg.contains("'data'")
        msg.contains('com.example.Foo')
        msg.contains('com.example.Bar')
        msg.contains('map(Foo)')
        msg.contains('TestMapper')
    }

    def 'produces one error per unresolved mapping'() {
        given:
        final fooType = Stub(TypeMirror) { toString() >> 'Foo' }
        final barType = Stub(TypeMirror) { toString() >> 'Bar' }
        final bazType = Stub(TypeMirror) { toString() >> 'Baz' }
        final gA = new GetterAccessor('a', fooType, Mock(ExecutableElement))
        final gB = new GetterAccessor('b', fooType, Mock(ExecutableElement))
        final wA = new ConstructorParamAccessor('a', barType, Mock(ExecutableElement), 0)
        final wB = new ConstructorParamAccessor('b', bazType, Mock(ExecutableElement), 1)

        final mapping1 = new ResolvedMapping([gA], 'a', wA, 'a', null, null)
        final mapping2 = new ResolvedMapping([gB], 'b', wB, 'b', null, null)
        final method = new MappingMethodModel(Mock(ExecutableElement), fooType, barType, [])
        final model = new ResolvedModel(Stub(TypeElement), [method], [(method): [mapping1, mapping2]], [:], [:])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().size() == 2
    }

    def 'fails with unmapped target error for each unmapped property'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'MyMapper' }
        final method = new MappingMethodModel(Mock(ExecutableElement), Mock(TypeMirror), Mock(TypeMirror), [])
        final model = new ResolvedModel(mapperType, [method], [(method): []], [(method): ['orphan'] as Set], [:])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().first().message.contains("Unmapped target property 'orphan'")
        result.errors().first().message.contains('MyMapper')
    }

    def 'fails with conflicting mappings error for each duplicate target'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'MyMapper' }
        final method = new MappingMethodModel(Mock(ExecutableElement), Mock(TypeMirror), Mock(TypeMirror), [])
        final model = new ResolvedModel(mapperType, [method], [(method): []], [:],
                [(method): [out: ['a', 'b'] as Set]])

        when:
        final result = stage.execute(model)

        then:
        !result.isSuccess()
        result.errors().first().message.contains("Conflicting mappings for target property 'out'")
        result.errors().first().message.contains('MyMapper')
    }

    private static singleEdgePath(final TypeMirror sourceType, final TypeMirror targetType) {
        final graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        final sourceNode = new TypeNode(sourceType, sourceType.toString())
        final targetNode = new TypeNode(targetType, targetType.toString())
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        graph.addEdge(sourceNode, targetNode, new TransformEdge(new DirectAssignableStrategy(), { input -> input }))
        return new BFSShortestPath<>(graph).getPath(sourceNode, targetNode)
    }
}
