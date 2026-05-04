package io.github.joke.percolate.processor.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.spi.SourceStep
import io.github.joke.percolate.processor.spi.Step
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
import java.util.stream.Stream

@Tag('unit')
class ResolveSourceChainsPhaseSpec extends Specification {

    def 'single-segment realisation emits REALISED and MARKER edges'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        def seedTarget = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.lastName')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addNode(seedTarget)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceRoot, seedTarget, directive))

        def getterRead = Mock(SourceStep)
        def step = new Step(mockTypeMirror('String'), Weights.STEP, (vars, inputs) -> null)
        getterRead.stepsFrom(_, 'lastName', _) >> Stream.of(step)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveSourceChainsPhase([getterRead], ctx)
        phase.apply(graph)

        then:
        def realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realisedEdges.size() == 1
        realisedEdges[0].weight == Weights.STEP
        realisedEdges[0].strategyClassFqn.isPresent()

        def markerEdges = graph.edges().filter { it.kind == EdgeKind.MARKER }.toList()
        markerEdges.size() == 1
        markerEdges[0].weight == Weights.NOOP
    }

    def 'no-match strategy emits zero edges'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        def seedTarget = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addNode(seedTarget)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceRoot, seedTarget, directive))

        def noMatchStep = Mock(SourceStep)
        noMatchStep.stepsFrom(_, _, _) >> Stream.empty()

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveSourceChainsPhase([noMatchStep], ctx)
        phase.apply(graph)

        then:
        graph.edges().filter { it.kind == EdgeKind.REALISED }.toList().isEmpty()
        graph.edges().filter { it.kind == EdgeKind.MARKER }.toList().isEmpty()

        // SEED edges are preserved
        graph.edges().filter { it.kind == EdgeKind.SEED }.toList().size() == 1
    }

    def 'MARKER weight is NOOP'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        def seedTarget = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addNode(seedTarget)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceRoot, seedTarget, directive))

        def step = new Step(mockTypeMirror('String'), Weights.STEP, (vars, inputs) -> null)
        def getterRead = Mock(SourceStep)
        getterRead.stepsFrom(_, _, _) >> Stream.of(step)

        def ctx = Mock(ResolveCtx)

        when:
        def phase = new ResolveSourceChainsPhase([getterRead], ctx)
        phase.apply(graph)

        then:
        def markers = graph.edges().filter { it.kind == EdgeKind.MARKER }.toList()
        markers.every { it.weight == Weights.NOOP }
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
