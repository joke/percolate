package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.TypeTransformEdge
import io.github.joke.percolate.processor.graph.TypedValueNode
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MatchedModel
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import io.github.joke.percolate.processor.transform.TransformProposal
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class BuildValueGraphStageSpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    TypeElement mapperType = Mock()

    // -------------------------------------------------------------------------
    // 6.2 + 6.3 + 6.4 — flat assignment
    // -------------------------------------------------------------------------

    def 'flat assignment builds SourceParamNode → PropertyNode → TargetSlotNode'() {
        given:
        final sourceType = typeMirror('test.Order')
        final targetType = typeMirror('test.OrderDTO')
        final stringType = typeMirror('java.lang.String')

        final nameGetter = readAccessor('name', stringType)
        final nameWriter = writeAccessor('name', stringType)
        final matching   = methodMatching(sourceType, targetType, [param('order', sourceType)],
                [MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)])

        final stage = stage(
                source: [(sourceType): [nameGetter]],
                target: [(targetType): [nameWriter]])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        final graph = result.value().graphs[matching]
        graph.vertexSet().any { it instanceof SourceParamNode && it.name == 'order' }
        final propNode = graph.vertexSet().find { it instanceof PropertyNode && it.name == 'name' }
        propNode != null
        final slotNode = graph.vertexSet().find { it instanceof TargetSlotNode && it.name == 'name' }
        slotNode != null
        graph.edgeSet().any { it instanceof PropertyReadEdge }
    }

    // -------------------------------------------------------------------------
    // 6.3 — nested chain reuses PropertyNode for shared prefix
    // -------------------------------------------------------------------------

    def 'two assignments sharing a source prefix reuse the same intermediate PropertyNode'() {
        given:
        final sourceType   = typeMirror('test.Order')
        final targetType   = typeMirror('test.OrderDTO')
        final customerType = typeMirror('test.Customer')
        final stringType   = typeMirror('java.lang.String')
        final intType      = typeMirror('int')

        final customerGetter = readAccessor('customer', customerType)
        final nameGetter     = readAccessor('name', stringType)
        final ageGetter      = readAccessor('age', intType)
        final nameWriter     = writeAccessor('customerName', stringType)
        final ageWriter      = writeAccessor('customerAge', intType)

        final a1 = MappingAssignment.of(['customer', 'name'], 'customerName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final a2 = MappingAssignment.of(['customer', 'age'], 'customerAge', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching = methodMatching(sourceType, targetType, [param('order', sourceType)], [a1, a2])

        final stage = stage(
                source: [(sourceType): [customerGetter], (customerType): [nameGetter, ageGetter]],
                target: [(targetType): [nameWriter, ageWriter]])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        final graph = result.value().graphs[matching]
        // Exactly one 'customer' PropertyNode
        final customerNodes = graph.vertexSet().findAll { it instanceof PropertyNode && it.name == 'customer' }
        customerNodes.size() == 1
        // Two outgoing PropertyReadEdges from the customer node
        final outEdges = graph.outgoingEdgesOf(customerNodes[0])
        outEdges.count { it instanceof PropertyReadEdge } == 2
    }

    // -------------------------------------------------------------------------
    // 6.3 — unresolvable segment records failure without aborting
    // -------------------------------------------------------------------------

    def 'unresolvable source segment records failure but still returns success'() {
        given:
        final sourceType = typeMirror('test.Order')
        final targetType = typeMirror('test.OrderDTO')

        // No properties available on sourceType
        final matching = methodMatching(sourceType, targetType, [param('order', sourceType)],
                [MappingAssignment.of(['missing'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)])

        final stage = stage(source: [:], target: [:])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        result.value().resolutionFailures.size() == 1
        final failure = result.value().resolutionFailures.values()[0]
        failure.segmentName == 'missing'
    }

    // -------------------------------------------------------------------------
    // 6.5 — fixpoint loop adds TypeTransformEdge via strategy
    // -------------------------------------------------------------------------

    def 'strategy proposal is added as TypeTransformEdge in the graph'() {
        given:
        final sourceType = typeMirror('test.Foo')
        final targetType = typeMirror('test.Bar')
        final fooType    = typeMirror('test.Foo')
        final barType    = typeMirror('test.Bar')

        final getter = readAccessor('value', fooType)
        final writer = writeAccessor('value', barType)
        final matching = methodMatching(sourceType, targetType, [param('src', sourceType)],
                [MappingAssignment.of(['value'], 'value', [:], null, AssignmentOrigin.EXPLICIT_MAP)])

        final innerStrategy = Stub(TypeTransformStrategy)
        final strategy = Stub(TypeTransformStrategy) {
            canProduce(_, _, _) >> { TypeMirror from, TypeMirror to, ctx ->
                if (from.toString() == 'test.Foo' && to.toString() == 'test.Bar') {
                    return Optional.of(new TransformProposal(fooType, barType, { it }, innerStrategy))
                }
                return Optional.empty()
            }
        }

        final stage = new BuildValueGraphStage(types, elements,
                [strategy],
                [sourceDisco([(sourceType): [getter]])],
                [targetDisco([(targetType): [writer]])])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        final graph = result.value().graphs[matching]
        graph.edgeSet().any { it instanceof TypeTransformEdge }
    }

    // -------------------------------------------------------------------------
    // 6.5 — no BFSShortestPath in BuildValueGraphStage
    // -------------------------------------------------------------------------

    def 'successful stage result contains no resolved paths (BFS belongs to ResolvePathStage)'() {
        given:
        final sourceType = typeMirror('test.Order')
        final targetType = typeMirror('test.OrderDTO')
        final stringType = typeMirror('java.lang.String')
        final matching   = methodMatching(sourceType, targetType, [param('order', sourceType)],
                [MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)])

        final stage = stage(
                source: [(sourceType): [readAccessor('name', stringType)]],
                target: [(targetType): [writeAccessor('name', stringType)]])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        // TypeTransformEdges have null codeTemplate — OptimizePathStage hasn't run
        result.value().graphs[matching].edgeSet()
                .findAll { it instanceof TypeTransformEdge }
                .every { it.codeTemplate == null }
    }

    // -------------------------------------------------------------------------
    // 6.5 — second assignment does not add duplicate PropertyReadEdge for shared prefix
    // -------------------------------------------------------------------------

    def 'second assignment with same source prefix does not duplicate PropertyReadEdge'() {
        given:
        final sourceType = typeMirror('test.Order')
        final targetType = typeMirror('test.OrderDTO')
        final strType    = typeMirror('java.lang.String')

        final getter  = readAccessor('name', strType)
        final writer1 = writeAccessor('firstName', strType)
        final writer2 = writeAccessor('lastName', strType)

        // Both assignments read the same 'name' property on the source
        final a1 = MappingAssignment.of(['name'], 'firstName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final a2 = MappingAssignment.of(['name'], 'lastName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching = methodMatching(sourceType, targetType, [param('order', sourceType)], [a1, a2])

        final stage = stage(
                source: [(sourceType): [getter]],
                target: [(targetType): [writer1, writer2]])

        when:
        final result = stage.execute(new MatchedModel(mapperType, [matching]))

        then:
        result.isSuccess()
        final graph = result.value().graphs[matching]
        // Only one PropertyNode for 'name'
        graph.vertexSet().count { it instanceof PropertyNode && it.name == 'name' } == 1
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BuildValueGraphStage stage(final Map config) {
        final srcMap = (config.source ?: [:]) as Map<TypeMirror, List>
        final tgtMap = (config.target ?: [:]) as Map<TypeMirror, List>
        new BuildValueGraphStage(types, elements, [], [sourceDisco(srcMap)], [targetDisco(tgtMap)])
    }

    private SourcePropertyDiscovery sourceDisco(final Map<TypeMirror, List> byType) {
        Stub(SourcePropertyDiscovery) {
            priority() >> 0
            discover(_, _, _) >> { TypeMirror t, e, ts -> byType.getOrDefault(t, []) }
        }
    }

    private TargetPropertyDiscovery targetDisco(final Map<TypeMirror, List> byType) {
        Stub(TargetPropertyDiscovery) {
            priority() >> 0
            discover(_, _, _) >> { TypeMirror t, e, ts -> byType.getOrDefault(t, []) }
        }
    }

    private MethodMatching methodMatching(
            final TypeMirror sourceType,
            final TypeMirror targetType,
            final List<VariableElement> params,
            final List<MappingAssignment> assignments) {
        final method = Stub(ExecutableElement) {
            getParameters() >> params
        }
        final model = new MappingMethodModel(method, sourceType, targetType, [])
        new MethodMatching(method, model, assignments)
    }

    private VariableElement param(final String name, final TypeMirror type) {
        Stub(VariableElement) {
            getSimpleName() >> Stub(Name) { toString() >> name }
            asType() >> type
        }
    }

    private ReadAccessor readAccessor(final String name, final TypeMirror type) {
        Stub(ReadAccessor) {
            getName() >> name
            getType() >> type
        }
    }

    private WriteAccessor writeAccessor(final String name, final TypeMirror type) {
        Stub(WriteAccessor) {
            getName() >> name
            getType() >> type
        }
    }

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) { toString() >> name }
    }
}
