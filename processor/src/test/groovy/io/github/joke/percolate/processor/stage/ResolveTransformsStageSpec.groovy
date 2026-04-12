package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.AccessEdge
import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.SourceRootNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ResolveTransformsStageSpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    TypeElement mapperType = Mock()

    def 'resolves single getter accessor chain and type transform for direct mapping'() {
        given:
        final nameType = Mock(TypeMirror)
        final nameGetter = new GetterAccessor('name', nameType, Mock(ExecutableElement))
        final nameWriter = new ConstructorParamAccessor('name', nameType, Mock(ExecutableElement), 0)

        final graph = symbolicGraph(sourceRoot: 'src',
                chain: ['name'], target: 'name')
        final method = new MappingMethodModel(Mock(ExecutableElement), nameType, nameType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        types.isAssignable(nameType, nameType) >> true
        final stage = stage(
                source: [(nameType): [nameGetter]],
                target: [(nameType): [nameWriter]])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        mappings[0].isResolved()
        mappings[0].sourceChain == [nameGetter]
        mappings[0].targetAccessor == nameWriter
        mappings[0].edges.every { it.codeTemplate != null }
    }

    def 'resolves two-level accessor chain for nested source property'() {
        given:
        final sourceType = Mock(TypeMirror)
        final addressType = Mock(TypeMirror)
        final stringType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final addressGetter = new GetterAccessor('address', addressType, Mock(ExecutableElement))
        final streetGetter = new GetterAccessor('street', stringType, Mock(ExecutableElement))
        final streetWriter = new ConstructorParamAccessor('street', stringType, Mock(ExecutableElement), 0)

        final graph = symbolicGraph(sourceRoot: 'src',
                chain: ['address', 'street'], target: 'street')
        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        types.isAssignable(stringType, stringType) >> true
        final stage = stage(
                source: [(sourceType): [addressGetter], (addressType): [streetGetter]],
                target: [(targetType): [streetWriter]])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        mappings[0].isResolved()
        mappings[0].sourceChain.size() == 2
        mappings[0].sourceChain[0] == addressGetter
        mappings[0].sourceChain[1] == streetGetter
    }

    def 'annotates failure when source chain segment cannot be resolved'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)

        final graph = symbolicGraph(sourceRoot: 'src', chain: ['unknown'], target: 'prop')
        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        final stage = stage(source: [:], target: [:])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        !mappings[0].isResolved()
        mappings[0].failure != null
        mappings[0].failure.segmentName == 'unknown'
        mappings[0].sourceChain.isEmpty()
    }

    def 'annotates failure when target accessor cannot be resolved'() {
        given:
        final sourceType = Mock(TypeMirror)
        final nameType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)
        final nameGetter = new GetterAccessor('name', nameType, Mock(ExecutableElement))

        final graph = symbolicGraph(sourceRoot: 'src', chain: ['name'], target: 'missing')
        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        final stage = stage(
                source: [(sourceType): [nameGetter]],
                target: [(targetType): []])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        !mappings[0].isResolved()
        mappings[0].failure != null
        mappings[0].failure.segmentName == 'missing'
        !mappings[0].sourceChain.isEmpty()
    }

    def 'records unmapped target when target property has no incoming mapping edge'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)

        final graph = new DefaultDirectedGraph<>(Object)
        graph.addVertex(new SourceRootNode('src'))
        graph.addVertex(new TargetPropertyNode('orphan'))

        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])
        final stage = stage(source: [:], target: [:])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        result.value().unmappedTargets[method] == ['orphan'] as Set
        result.value().methodMappings[method].isEmpty()
    }

    def 'records duplicate target when two source properties map to the same target'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Mock(TypeMirror)

        final graph = new DefaultDirectedGraph<>(Object)
        final sourceRoot = new SourceRootNode('src')
        final srcA = new SourcePropertyNode('a')
        final srcB = new SourcePropertyNode('b')
        final target = new TargetPropertyNode('out')
        [sourceRoot, srcA, srcB, target].each { graph.addVertex(it) }
        graph.addEdge(sourceRoot, srcA, new AccessEdge())
        graph.addEdge(sourceRoot, srcB, new AccessEdge())
        graph.addEdge(srcA, target, new MappingEdge())
        graph.addEdge(srcB, target, new MappingEdge())

        final method = new MappingMethodModel(Mock(ExecutableElement), sourceType, targetType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])
        final stage = stage(source: [:], target: [:])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        result.value().duplicateTargets[method].containsKey('out')
        result.value().duplicateTargets[method]['out'] == ['a', 'b'] as Set
    }

    // Task 5.4: ResolveTransformsStage creates per-mapping ResolutionContext with using
    def 'resolved mapping carries using value from MappingEdge'() {
        given:
        final nameType = Mock(TypeMirror)
        final nameGetter = new GetterAccessor('name', nameType, Mock(ExecutableElement))
        final nameWriter = new ConstructorParamAccessor('name', nameType, Mock(ExecutableElement), 0)

        final graph = new DefaultDirectedGraph<>(Object)
        final sourceRoot = new SourceRootNode('src')
        final sourceProp = new SourcePropertyNode('name')
        final targetProp = new TargetPropertyNode('name')
        [sourceRoot, sourceProp, targetProp].each { graph.addVertex(it) }
        graph.addEdge(sourceRoot, sourceProp, new AccessEdge())
        graph.addEdge(sourceProp, targetProp, new MappingEdge([:], 'toName'))

        final method = new MappingMethodModel(Mock(ExecutableElement), nameType, nameType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        types.isAssignable(nameType, nameType) >> true
        final stage = stage(
                source: [(nameType): [nameGetter]],
                target: [(nameType): [nameWriter]])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        mappings[0].using == 'toName'
    }

    def 'resolved mapping has empty using when MappingEdge has no using'() {
        given:
        final nameType = Mock(TypeMirror)
        final nameGetter = new GetterAccessor('name', nameType, Mock(ExecutableElement))
        final nameWriter = new ConstructorParamAccessor('name', nameType, Mock(ExecutableElement), 0)

        final graph = symbolicGraph(sourceRoot: 'src', chain: ['name'], target: 'name')
        final method = new MappingMethodModel(Mock(ExecutableElement), nameType, nameType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        types.isAssignable(nameType, nameType) >> true
        final stage = stage(
                source: [(nameType): [nameGetter]],
                target: [(nameType): [nameWriter]])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        result.value().methodMappings[method][0].using == ''
    }

    def 'marks unresolvable transform as unresolved without failure'() {
        given:
        final fooType = Mock(TypeMirror)
        final barType = Mock(TypeMirror)
        final fooGetter = new GetterAccessor('data', fooType, Mock(ExecutableElement))
        final barWriter = new ConstructorParamAccessor('data', barType, Mock(ExecutableElement), 0)

        final graph = symbolicGraph(sourceRoot: 'src', chain: ['data'], target: 'data')
        final method = new MappingMethodModel(Mock(ExecutableElement), fooType, barType, [])
        final mappingGraph = new MappingGraph(mapperType, [method], [(method): graph])

        types.isAssignable(_, _) >> false
        elements.getAllMembers(mapperType) >> []
        final stage = new ResolveTransformsStage(types, elements, [new DirectAssignableStrategy()],
                [discoveryFor([(fooType): [fooGetter]])],
                [targetDiscoveryFor([(barType): [barWriter]])])

        expect:
        final result = stage.execute(mappingGraph)
        result.isSuccess()
        final mappings = result.value().methodMappings[method]
        mappings.size() == 1
        !mappings[0].isResolved()
        mappings[0].failure == null
    }

    private ResolveTransformsStage stage(final Map<String, Map> config) {
        final srcMap = (config.source ?: [:]) as Map<TypeMirror, List>
        final tgtMap = (config.target ?: [:]) as Map<TypeMirror, List>
        return new ResolveTransformsStage(types, elements, [new DirectAssignableStrategy()],
                [discoveryFor(srcMap)], [targetDiscoveryFor(tgtMap)])
    }

    private SourcePropertyDiscovery discoveryFor(final Map<TypeMirror, List> byType) {
        Stub(SourcePropertyDiscovery) {
            priority() >> 0
            discover(_, _, _) >> { TypeMirror t, e, types -> byType.getOrDefault(t, []) }
        }
    }

    private TargetPropertyDiscovery targetDiscoveryFor(final Map<TypeMirror, List> byType) {
        Stub(TargetPropertyDiscovery) {
            priority() >> 0
            discover(_, _, _) >> { TypeMirror t, e, types -> byType.getOrDefault(t, []) }
        }
    }

    private DefaultDirectedGraph<Object, Object> symbolicGraph(
            final Map<String, Object> config) {
        final String sourceRootName = config.sourceRoot as String
        final List<String> chain = config.chain as List<String>
        final String target = config.target as String

        final graph = new DefaultDirectedGraph<>(Object)
        final sourceRoot = new SourceRootNode(sourceRootName)
        graph.addVertex(sourceRoot)

        Object prev = sourceRoot
        for (final segment in chain) {
            final node = new SourcePropertyNode(segment)
            graph.addVertex(node)
            graph.addEdge(prev, node, new AccessEdge())
            prev = node
        }

        final targetNode = new TargetPropertyNode(target)
        graph.addVertex(targetNode)
        graph.addEdge(prev, targetNode, new MappingEdge())
        return graph
    }
}
