package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.DiscoveredMethod
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.spi.MethodCallStrategy
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ResolveTransformsStageSpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()

    def 'resolves assignable types via DirectAssignableStrategy'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final sourceAccessor = new GetterAccessor('name', sourceType, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('name', targetType, Mock(ExecutableElement), 0)

        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s = new SourcePropertyNode('name', sourceType, sourceAccessor)
        final t = new TargetPropertyNode('name', targetType, targetAccessor)
        g.addVertex(s)
        g.addVertex(t)
        g.addEdge(s, t, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final discovered = new DiscoveredMethod(method, [name: sourceAccessor], [name: targetAccessor])
        final mappingGraph = new MappingGraph(Mock(TypeElement), [discovered], [(discovered): g])

        types.isAssignable(sourceType, targetType) >> true

        final stage = new ResolveTransformsStage(types, elements, [new DirectAssignableStrategy()])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[discovered]
        mappings.size() == 1
        mappings[0].isResolved()
        mappings[0].edges.size() == 1
        mappings[0].edges[0].strategy instanceof DirectAssignableStrategy
    }

    def 'resolves non-assignable types with sibling method via MethodCallStrategy'() {
        given:
        final addressType = Mock(TypeMirror)
        final addressDtoType = Mock(TypeMirror)
        final stringType = Mock(TypeMirror)

        final sourceAccessor = new GetterAccessor('billingAddress', addressType, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('address', addressDtoType, Mock(ExecutableElement), 0)

        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s = new SourcePropertyNode('billingAddress', addressType, sourceAccessor)
        final t = new TargetPropertyNode('address', addressDtoType, targetAccessor)
        g.addVertex(s)
        g.addVertex(t)
        g.addEdge(s, t, new MappingEdge(MappingEdge.Type.DIRECT))

        final mainMethod = new MappingMethodModel(Mock(ExecutableElement), stringType, stringType, [])
        final mainDiscovered = new DiscoveredMethod(mainMethod, [billingAddress: sourceAccessor], [address: targetAccessor])

        final methodName = Stub(Name) { toString() >> 'mapAddress' }
        final siblingExec = Stub(ExecutableElement) { getSimpleName() >> methodName }
        final siblingMethod = new MappingMethodModel(siblingExec, addressType, addressDtoType, [])
        final siblingDiscovered = new DiscoveredMethod(siblingMethod, [:], [:])

        final siblingGraph = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final mappingGraph = new MappingGraph(Mock(TypeElement),
                [mainDiscovered, siblingDiscovered],
                [(mainDiscovered): g, (siblingDiscovered): siblingGraph])

        types.isAssignable(addressType, addressDtoType) >> false
        types.isAssignable(addressType, addressType) >> true
        types.isAssignable(addressDtoType, addressDtoType) >> true

        final stage = new ResolveTransformsStage(types, elements, [new DirectAssignableStrategy(), new MethodCallStrategy()])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[mainDiscovered]
        mappings.size() == 1
        mappings[0].isResolved()
        mappings[0].edges.size() == 1
        mappings[0].edges[0].strategy instanceof MethodCallStrategy
    }

    def 'marks non-assignable types without matching strategy as unresolved'() {
        given:
        final fooType = Mock(TypeMirror)
        final barType = Mock(TypeMirror)

        final sourceAccessor = new GetterAccessor('data', fooType, Mock(ExecutableElement))
        final targetAccessor = new ConstructorParamAccessor('data', barType, Mock(ExecutableElement), 0)

        final g = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final s = new SourcePropertyNode('data', fooType, sourceAccessor)
        final t = new TargetPropertyNode('data', barType, targetAccessor)
        g.addVertex(s)
        g.addVertex(t)
        g.addEdge(s, t, new MappingEdge(MappingEdge.Type.DIRECT))

        final method = new MappingMethodModel(Mock(ExecutableElement), fooType, barType, [])
        final discovered = new DiscoveredMethod(method, [data: sourceAccessor], [data: targetAccessor])
        final mappingGraph = new MappingGraph(Mock(TypeElement), [discovered], [(discovered): g])

        types.isAssignable(_, _) >> false

        final stage = new ResolveTransformsStage(types, elements, [new DirectAssignableStrategy(), new MethodCallStrategy()])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[discovered]
        mappings.size() == 1
        !mappings[0].isResolved()
    }
}
