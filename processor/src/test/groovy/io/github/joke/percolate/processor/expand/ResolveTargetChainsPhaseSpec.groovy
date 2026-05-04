package io.github.joke.percolate.processor.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.spi.GroupTarget
import io.github.joke.percolate.processor.spi.GroupBuild
import io.github.joke.percolate.processor.spi.Slot
import io.github.joke.percolate.processor.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.Optional

@Tag('unit')
class ResolveTargetChainsPhaseSpec extends Specification {

    def 'exact-match emits group with shared groupId'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def returnRoot = new Node(Optional.of(mockTypeMirror('Human')), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        def slot1 = new Node(Optional.empty(), new TargetLocation(mockTargetPath('firstName')), scope, Optional.empty())
        def slot2 = new Node(Optional.empty(), new TargetLocation(mockTargetPath('lastName')), scope, Optional.empty())
        graph.addNode(returnRoot)
        graph.addNode(slot1)
        graph.addNode(slot2)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(slot1, returnRoot, directive))
        graph.addEdge(Edge.seed(slot2, returnRoot, directive))

        def build = new GroupBuild(
                [new Slot('firstName', mockTypeMirror('String'), Weights.STEP),
                 new Slot('lastName', mockTypeMirror('String'), Weights.STEP)],
                (vars, inputs) -> null)
        def ctorCall = Mock(GroupTarget)
        ctorCall.buildFor(_, _, _) >> Optional.of(build)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveTargetChainsPhase([ctorCall], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 2
        realisedEdges[0].groupId.isPresent()
        realisedEdges[1].groupId.isPresent()
        realisedEdges[0].groupId.get() == realisedEdges[1].groupId.get()

        def markers = graph.edges().filter { it.kind == EdgeKind.MARKER }.toList()
        markers.size() == 2
    }

    def 'multiple parallel groups when multiple GroupTargets match'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def returnRoot = new Node(Optional.of(mockTypeMirror('Human')), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        def slot1 = new Node(Optional.empty(), new TargetLocation(mockTargetPath('firstName')), scope, Optional.empty())
        graph.addNode(returnRoot)
        graph.addNode(slot1)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(slot1, returnRoot, directive))

        def build1 = new GroupBuild(
                [new Slot('firstName', mockTypeMirror('String'), Weights.STEP)],
                (vars, inputs) -> null)
        def build2 = new GroupBuild(
                [new Slot('firstName', mockTypeMirror('String'), Weights.STEP)],
                (vars, inputs) -> null)
        def groupTarget1 = Mock(GroupTarget)
        def groupTarget2 = Mock(GroupTarget)
        groupTarget1.buildFor(_, _, _) >> Optional.of(build1)
        groupTarget2.buildFor(_, _, _) >> Optional.of(build2)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveTargetChainsPhase([groupTarget1, groupTarget2], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 2

        def groupIds = realisedEdges.collect { it.groupId.get() }.toSet()
        groupIds.size() == 2
    }

    def 'no-match emits zero edges'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def returnRoot = new Node(Optional.of(mockTypeMirror('Human')), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        def slot1 = new Node(Optional.empty(), new TargetLocation(mockTargetPath('firstName')), scope, Optional.empty())
        graph.addNode(returnRoot)
        graph.addNode(slot1)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(slot1, returnRoot, directive))

        def noMatch = Mock(GroupTarget)
        noMatch.buildFor(_, _, _) >> Optional.empty()

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveTargetChainsPhase([noMatch], ctx)
        phase.apply(graph)

        then:
        graph.edges().filter { it.kind == EdgeKind.REALISED }.toList().isEmpty()
        graph.edges().filter { it.kind == EdgeKind.MARKER }.toList().isEmpty()
    }

    def 'addGroupCodegen registered on graph'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def returnRoot = new Node(Optional.of(mockTypeMirror('Human')), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        def slot1 = new Node(Optional.empty(), new TargetLocation(mockTargetPath('firstName')), scope, Optional.empty())
        graph.addNode(returnRoot)
        graph.addNode(slot1)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(slot1, returnRoot, directive))

        def build = new GroupBuild(
                [new Slot('firstName', mockTypeMirror('String'), Weights.STEP)],
                { CodeBlock b -> b })
        def groupTarget = Mock(GroupTarget)
        groupTarget.buildFor(_, _, _) >> Optional.of(build)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveTargetChainsPhase([groupTarget], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 1
        def groupId = realisedEdges[0].groupId.get()
        graph.groupCodegen(groupId).isPresent()
    }

    private TypeMirror mockTypeMirror(String typeName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> typeName
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        typeMirror
    }

    private TargetPath mockTargetPath(String path) {
        if (path.isEmpty()) {
            return new TargetPath([])
        }
        new TargetPath(path.split('\\.').toList())
    }
    private ExecutableElement mockMethod(String name) {
        def m = Mock(ExecutableElement)
        def n = Mock(Name)
        n.toString() >> name
        m.getSimpleName() >> n
        m.parameters >> []
        def retType = Mock(TypeMirror)
        retType.toString() >> "Human"
        m.getReturnType() >> retType
        m
    }
}
