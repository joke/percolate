package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.BridgeStep
import io.github.joke.percolate.processor.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types
import java.util.stream.Stream

@Tag('unit')
class BridgeSourceToTargetPhaseSpec extends Specification {

    def 'DirectAssign realises identity bridge'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def stringType = mockTypeMirror('String')
        def sourceNode = new Node(Optional.of(stringType), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def targetNode = new Node(Optional.of(stringType), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceNode)
        graph.addNode(targetNode)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceNode, targetNode, directive))

        def bridgeStep = new BridgeStep(stringType, stringType, Weights.NOOP, (vars, inputs) -> null, List.of())
        def directAssign = Mock(Bridge)
        directAssign.bridge(_, _, _) >> Stream.of(bridgeStep)

        def types = Mock(Types)
        types.isSameType(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types

        when:
        def phase = new BridgeSourceToTargetPhase([directAssign], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 1
        realisedEdges[0].weight == Weights.NOOP
        realisedEdges[0].strategyClassFqn.isPresent()
    }

    def 'skipped when either side has no realisation'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def sourceNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        def targetNode = new Node(Optional.empty(), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceNode)
        graph.addNode(targetNode)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceNode, targetNode, directive))

        def bridge = Mock(Bridge)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new BridgeSourceToTargetPhase([bridge], ctx)
        phase.apply(graph)

        then:
        graph.edges().filter { it.kind == EdgeKind.REALISED }.toList().isEmpty()
        0 * bridge.bridge(_, _, _)
    }

    def 'multiple Bridge matches emit parallel REALISED edges'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def stringType = mockTypeMirror('String')
        def sourceNode = new Node(Optional.of(stringType), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def targetNode = new Node(Optional.of(stringType), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceNode)
        graph.addNode(targetNode)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceNode, targetNode, directive))

        def bridgeStep = new BridgeStep(stringType, stringType, Weights.NOOP, (vars, inputs) -> null, List.of())
        // Use anonymous class instances so each has a distinct getClass().getName()
        def bridge1 = new Bridge() {
            Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx) { Stream.of(bridgeStep) }
        }
        def bridge2 = new Bridge() {
            Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx) { Stream.of(bridgeStep) }
        }

        def types = Mock(Types)
        types.isSameType(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types

        when:
        def phase = new BridgeSourceToTargetPhase([bridge1, bridge2], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 2
    }

    private TypeMirror mockTypeMirror(String typeName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> typeName
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        typeMirror
    }

    private AccessPath mockAccessPath(String path) {
        new AccessPath(path.split('\\.').toList())
    }

    private TargetPath mockTargetPath(String path) {
        new TargetPath(path.isEmpty() ? [] : path.split('\\.').toList())
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
