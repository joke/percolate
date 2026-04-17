package io.github.joke.percolate.processor.stage

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.LiftEdge
import io.github.joke.percolate.processor.graph.LiftKind
import io.github.joke.percolate.processor.graph.NullWidenEdge
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.TypeTransformEdge
import io.github.joke.percolate.processor.graph.TypedValueNode
import io.github.joke.percolate.processor.graph.ValueEdge
import io.github.joke.percolate.processor.graph.ValueNode
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolvedAssignment
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.BFSShortestPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class OptimizePathStageSpec extends Specification {

    // -------------------------------------------------------------------------
    // 9.2 — TypeTransformEdge on resolved path gets its codeTemplate set
    // -------------------------------------------------------------------------

    def 'TypeTransformEdge on resolved path gets codeTemplate materialised'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final strategy   = Stub(TypeTransformStrategy)
        final template   = { input -> CodeBlock.of('str($L)', input) }

        final param   = makeParam('order', orderType)
        final getter  = readAccessor('name', stringType)
        final writer  = writeAccessor('name', stringType)

        final graph   = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src     = new SourceParamNode(param, orderType)
        final prop    = new PropertyNode('name', stringType, getter)
        final slot    = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        final xfmEdge = new TypeTransformEdge(strategy, stringType, stringType, template)
        graph.addEdge(prop, slot, xfmEdge)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final ra   = resolvedAssignment(path)
        final matching = methodMatching(orderType, stringType, [param], [ra.assignment])

        when:
        final out = new OptimizePathStage().execute([(matching): [ra]])

        then:
        out.isSuccess()
        xfmEdge.codeTemplate != null
        xfmEdge.codeTemplate.apply(CodeBlock.of('x')).toString() == 'str(x)'
    }

    // -------------------------------------------------------------------------
    // 9.4 — Off-path TypeTransformEdge keeps codeTemplate == null
    // -------------------------------------------------------------------------

    def 'off-path TypeTransformEdge is not materialised'() {
        given:
        final intType    = typeMirror('int')
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final strategy   = Stub(TypeTransformStrategy)
        final template   = { input -> CodeBlock.of('str($L)', input) }

        final param     = makeParam('order', orderType)
        final getter1   = readAccessor('name', stringType)
        final getter2   = readAccessor('count', intType)
        final writer    = writeAccessor('name', stringType)

        final graph     = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src       = new SourceParamNode(param, orderType)
        final nameProp  = new PropertyNode('name', stringType, getter1)
        final countProp = new PropertyNode('count', intType, getter2)
        final slot      = new TargetSlotNode('name', stringType, writer)
        [src, nameProp, countProp, slot].each { graph.addVertex(it) }
        graph.addEdge(src, nameProp, new PropertyReadEdge())
        graph.addEdge(src, countProp, new PropertyReadEdge())

        // On-path edge: nameProp -> slot
        final onPathEdge  = new TypeTransformEdge(strategy, stringType, stringType, template)
        graph.addEdge(nameProp, slot, onPathEdge)

        // Off-path edge: countProp -> slot (no BFS path uses this)
        final offPathEdge = new TypeTransformEdge(strategy, intType, stringType, template)
        graph.addEdge(countProp, slot, offPathEdge)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final ra   = resolvedAssignment(path)
        final matching = methodMatching(orderType, stringType, [param], [ra.assignment])

        when:
        new OptimizePathStage().execute([(matching): [ra]])

        then:
        onPathEdge.codeTemplate  != null
        offPathEdge.codeTemplate == null
    }

    // -------------------------------------------------------------------------
    // 9.3 — LiftEdge on resolved path: inner templates composed, outer template set
    // -------------------------------------------------------------------------

    def 'LiftEdge on resolved path gets codeTemplate derived from inner path'() {
        given:
        final optStringType = typeMirror('Optional<String>')
        final optIntType    = typeMirror('Optional<Integer>')
        final stringType    = typeMirror('java.lang.String')
        final intType       = typeMirror('java.lang.Integer')
        final orderType     = typeMirror('test.Order')
        final strategy      = Stub(TypeTransformStrategy)

        // Inner path: String -> Integer (represents element conversion)
        final innerGraph    = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final innerStrNode  = new TypedValueNode(stringType, 'String')
        final innerIntNode  = new TypedValueNode(intType, 'Integer')
        innerGraph.addVertex(innerStrNode)
        innerGraph.addVertex(innerIntNode)
        final innerXfmEdge  = new TypeTransformEdge(strategy, stringType, intType, { input -> CodeBlock.of('Integer.parseInt($L)', input) })
        innerGraph.addEdge(innerStrNode, innerIntNode, innerXfmEdge)
        final innerPath = new BFSShortestPath<>(innerGraph).getPath(innerStrNode, innerIntNode)

        final param  = makeParam('order', orderType)
        final getter = readAccessor('tag', optStringType)
        final writer = writeAccessor('value', optIntType)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(param, orderType)
        final prop  = new PropertyNode('tag', optStringType, getter)
        final slot  = new TargetSlotNode('value', optIntType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        final liftEdge = new LiftEdge(LiftKind.OPTIONAL, innerPath)
        graph.addEdge(prop, slot, liftEdge)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final ra   = resolvedAssignment(path)
        final matching = methodMatching(orderType, optIntType, [param], [ra.assignment])

        when:
        new OptimizePathStage().execute([(matching): [ra]])

        then:
        liftEdge.codeTemplate != null
        innerXfmEdge.codeTemplate != null
        liftEdge.codeTemplate.apply(CodeBlock.of('x')).toString() == 'x.map(e -> Integer.parseInt(e))'
    }

    // -------------------------------------------------------------------------
    // 9.5 — NullWidenEdge on path causes IllegalStateException
    // -------------------------------------------------------------------------

    def 'NullWidenEdge on resolved path throws IllegalStateException'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')

        final param  = makeParam('order', orderType)
        final getter = readAccessor('name', stringType)
        final writer = writeAccessor('name', stringType)

        final graph  = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src    = new SourceParamNode(param, orderType)
        final prop   = new PropertyNode('name', stringType, getter)
        final slot   = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        graph.addEdge(prop, slot, new NullWidenEdge())

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final ra   = resolvedAssignment(path)
        final matching = methodMatching(orderType, stringType, [param], [ra.assignment])

        when:
        new OptimizePathStage().execute([(matching): [ra]])

        then:
        thrown(IllegalStateException)
    }

    // -------------------------------------------------------------------------
    // 9.4 — Unresolved assignment is skipped (codeTemplate stays null)
    // -------------------------------------------------------------------------

    def 'unresolved ResolvedAssignment is skipped entirely'() {
        given:
        final stringType  = typeMirror('java.lang.String')
        final orderType   = typeMirror('test.Order')
        final strategy    = Stub(TypeTransformStrategy)
        final template    = { input -> CodeBlock.of('str($L)', input) }

        final xfmEdge = new TypeTransformEdge(strategy, stringType, stringType, template)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, null, null)
        final param = makeParam('order', orderType)
        final matching = methodMatching(orderType, stringType, [param], [assignment])

        when:
        new OptimizePathStage().execute([(matching): [ra]])

        then:
        xfmEdge.codeTemplate == null
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResolvedAssignment resolvedAssignment(final GraphPath<ValueNode, ValueEdge> path) {
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        new ResolvedAssignment(assignment, path, null)
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
