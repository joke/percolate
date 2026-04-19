package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.TypeTransformEdge
import io.github.joke.percolate.processor.graph.TypedValueNode
import io.github.joke.percolate.processor.graph.ValueEdge
import io.github.joke.percolate.processor.graph.ValueGraphResult
import io.github.joke.percolate.processor.graph.ValueNode
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MatchedModel
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolutionFailure
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ResolvePathStageSpec extends Specification {

    // -------------------------------------------------------------------------
    // 8.2 — flat assignment resolves to [PropertyReadEdge, TypeTransformEdge]
    // -------------------------------------------------------------------------

    def 'flat assignment resolves to [PropertyReadEdge, TypeTransformEdge]'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final strategy   = Stub(TypeTransformStrategy)

        final param   = makeParam('order', orderType)
        final nameGetter = readAccessor('name', stringType)
        final nameWriter = writeAccessor('name', stringType)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final srcNode  = new SourceParamNode(param, orderType)
        final propNode = new PropertyNode('name', stringType)
        final slotNode = new TargetSlotNode('name', stringType, nameWriter)
        graph.addVertex(srcNode)
        graph.addVertex(propNode)
        graph.addVertex(slotNode)
        final readEdge      = new PropertyReadEdge({ input -> input })
        final transformEdge = new TypeTransformEdge(strategy, stringType, stringType, { it })
        graph.addEdge(srcNode, propNode, readEdge)
        graph.addEdge(propNode, slotNode, transformEdge)

        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final matching   = methodMatching(orderType, stringType, [param], [assignment])
        final result     = new ValueGraphResult([(matching): graph], [:])

        when:
        final out = new ResolvePathStage().execute(result)

        then:
        out.isSuccess()
        final list = out.value()[matching]
        list.size() == 1
        final ra = list[0]
        ra.resolved
        ra.path.edgeList == [readEdge, transformEdge]
        ra.readChainEdges == [readEdge]
    }

    // -------------------------------------------------------------------------
    // 8.2 — nested chain: two assignments sharing the same PropertyNode
    // -------------------------------------------------------------------------

    def 'two assignments through shared PropertyNode each get correct path'() {
        given:
        final orderType    = typeMirror('test.Order')
        final customerType = typeMirror('test.Customer')
        final stringType   = typeMirror('java.lang.String')
        final intType      = typeMirror('int')
        final strategy     = Stub(TypeTransformStrategy)

        final param          = makeParam('order', orderType)
        final custGetter     = readAccessor('customer', customerType)
        final nameGetter     = readAccessor('name', stringType)
        final ageGetter      = readAccessor('age', intType)
        final custNameWriter = writeAccessor('customerName', stringType)
        final custAgeWriter  = writeAccessor('customerAge', intType)

        final graph      = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final srcNode    = new SourceParamNode(param, orderType)
        final custNode   = new PropertyNode('customer', customerType)
        final nameNode   = new PropertyNode('name', stringType)
        final ageNode    = new PropertyNode('age', intType)
        final nameSlot   = new TargetSlotNode('customerName', stringType, custNameWriter)
        final ageSlot    = new TargetSlotNode('customerAge', intType, custAgeWriter)
        [srcNode, custNode, nameNode, ageNode, nameSlot, ageSlot].each { graph.addVertex(it) }

        final srcCustEdge  = new PropertyReadEdge({ input -> input })
        final custNameEdge = new PropertyReadEdge({ input -> input })
        final custAgeEdge  = new PropertyReadEdge({ input -> input })
        final nameXfm      = new TypeTransformEdge(strategy, stringType, stringType, { it })
        final ageXfm       = new TypeTransformEdge(strategy, intType, intType, { it })
        graph.addEdge(srcNode, custNode, srcCustEdge)
        graph.addEdge(custNode, nameNode, custNameEdge)
        graph.addEdge(custNode, ageNode, custAgeEdge)
        graph.addEdge(nameNode, nameSlot, nameXfm)
        graph.addEdge(ageNode, ageSlot, ageXfm)

        final a1       = MappingAssignment.of(['customer', 'name'], 'customerName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final a2       = MappingAssignment.of(['customer', 'age'], 'customerAge', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching = methodMatching(orderType, stringType, [param], [a1, a2])
        final result   = new ValueGraphResult([(matching): graph], [:])

        when:
        final out = new ResolvePathStage().execute(result)

        then:
        out.isSuccess()
        final list = out.value()[matching]
        list.size() == 2
        // name path: srcCustEdge, custNameEdge, nameXfm
        final nameRa = list.find { it.assignment == a1 }
        nameRa.resolved
        nameRa.path.edgeList == [srcCustEdge, custNameEdge, nameXfm]
        // age path: srcCustEdge, custAgeEdge, ageXfm
        final ageRa = list.find { it.assignment == a2 }
        ageRa.resolved
        ageRa.path.edgeList == [srcCustEdge, custAgeEdge, ageXfm]
    }

    // -------------------------------------------------------------------------
    // 8.5 — code templates are eagerly set at edge construction
    // -------------------------------------------------------------------------

    def 'TypeTransformEdge codeTemplate is non-null after construction'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final strategy   = Stub(TypeTransformStrategy)
        final xfmEdge    = new TypeTransformEdge(strategy, stringType, stringType, { it })

        expect:
        xfmEdge.codeTemplate != null
    }

    // -------------------------------------------------------------------------
    // 8.6 — failed resolution: access-chain failure from BuildValueGraphStage
    // -------------------------------------------------------------------------

    def 'assignment with access-chain failure produces unresolved ResolvedAssignment'() {
        given:
        final orderType  = typeMirror('test.Order')
        final stringType = typeMirror('java.lang.String')

        final param      = makeParam('order', orderType)
        final graph      = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final srcNode    = new SourceParamNode(param, orderType)
        graph.addVertex(srcNode)

        final assignment = MappingAssignment.of(['missing'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = methodMatching(orderType, stringType, [param], [assignment])
        final failure    = new ResolutionFailure('missing', [] as Set)
        final result     = new ValueGraphResult([(matching): graph], [(assignment): failure])

        when:
        final out = new ResolvePathStage().execute(result)

        then:
        out.isSuccess()
        final ra = out.value()[matching][0]
        !ra.resolved
        ra.path == null
        ra.failure == failure
    }

    // -------------------------------------------------------------------------
    // 8.6 — no path: unresolved without access-chain failure
    // -------------------------------------------------------------------------

    def 'no path between source and target yields unresolved ResolvedAssignment'() {
        given:
        final orderType  = typeMirror('test.Order')
        final fooType    = typeMirror('test.Foo')
        final stringType = typeMirror('java.lang.String')

        final param   = makeParam('order', orderType)
        final getter  = readAccessor('foo', fooType)
        final writer  = writeAccessor('bar', stringType)

        final graph   = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src     = new SourceParamNode(param, orderType)
        final prop    = new PropertyNode('foo', fooType)
        final slot    = new TargetSlotNode('bar', stringType, writer)
        // No edge connecting prop to slot — intentional gap
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge({ input -> input }))

        final assignment = MappingAssignment.of(['foo'], 'bar', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = methodMatching(orderType, stringType, [param], [assignment])

        when:
        final out = new ResolvePathStage().execute(new ValueGraphResult([(matching): graph], [:]))

        then:
        out.isSuccess()
        final ra = out.value()[matching][0]
        !ra.resolved
        ra.path == null
        ra.failure == null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private VariableElement makeParam(final String name, final TypeMirror type) {
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
