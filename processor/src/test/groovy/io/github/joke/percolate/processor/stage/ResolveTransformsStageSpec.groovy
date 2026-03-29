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
import io.github.joke.percolate.processor.transform.DirectOperation
import io.github.joke.percolate.processor.transform.SubMapOperation
import io.github.joke.percolate.processor.transform.UnresolvedOperation
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

@Tag('unit')
class ResolveTransformsStageSpec extends Specification {

    Types types = Mock()
    ResolveTransformsStage stage = new ResolveTransformsStage(types)

    def 'resolves assignable types as DIRECT'() {
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

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[discovered]
        mappings.size() == 1
        mappings[0].chain.size() == 1
        mappings[0].chain[0].operation instanceof DirectOperation
    }

    def 'resolves non-assignable types with sibling method as SUBMAP'() {
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

        final siblingMethod = new MappingMethodModel(Mock(ExecutableElement), addressType, addressDtoType, [])
        final siblingDiscovered = new DiscoveredMethod(siblingMethod, [:], [:])

        final siblingGraph = new DefaultDirectedGraph<PropertyNode, MappingEdge>(MappingEdge)
        final mappingGraph = new MappingGraph(Mock(TypeElement),
                [mainDiscovered, siblingDiscovered],
                [(mainDiscovered): g, (siblingDiscovered): siblingGraph])

        types.isAssignable(addressType, addressDtoType) >> false
        types.isAssignable(addressType, addressType) >> true
        types.isAssignable(addressDtoType, addressDtoType) >> true

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[mainDiscovered]
        mappings.size() == 1
        mappings[0].chain.size() == 1
        final op = mappings[0].chain[0].operation
        op instanceof SubMapOperation
        (op as SubMapOperation).targetMethod == siblingDiscovered
    }

    def 'marks non-assignable types without sibling method as UNRESOLVED'() {
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

        types.isAssignable(fooType, barType) >> false
        types.isAssignable(_, _) >> false

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[discovered]
        mappings.size() == 1
        mappings[0].chain.size() == 1
        final op = mappings[0].chain[0].operation
        op instanceof UnresolvedOperation
        (op as UnresolvedOperation).sourceType == fooType
        (op as UnresolvedOperation).targetType == barType
    }
}
