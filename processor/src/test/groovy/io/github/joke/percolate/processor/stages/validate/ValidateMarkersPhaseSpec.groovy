package io.github.joke.percolate.processor.stages.validate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.graph.*
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ValidateMarkersPhaseSpec extends Specification {

    def 'unrealised ?-typed seed emits one error'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seedNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        graph.addNode(seedNode)
        def directive = Mock(AnnotationMirror)
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addEdge(Edge.seed(sourceRoot, seedNode, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidateMarkersPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        1 * diagnostics.error(_, directive, null, { it != null && it.contains('No strategy could realise') })
    }

    def 'error is keyed to the directive AnnotationMirror'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seedNode = new Node(Optional.empty(), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(seedNode)
        def directive = Mock(AnnotationMirror)
        def returnRoot = new Node(Optional.of(mockTypeMirror('Human')), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        graph.addNode(returnRoot)
        graph.addEdge(Edge.seed(seedNode, returnRoot, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidateMarkersPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        1 * diagnostics.error(_, directive, null, _)
    }

    def 'mapper is scarred on failure'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seedNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        graph.addNode(seedNode)
        def directive = Mock(AnnotationMirror)
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addEdge(Edge.seed(sourceRoot, seedNode, directive))

        def diagnostics = Mock(Diagnostics)
        diagnostics.hasErrorsFor(_) >> true

        when:
        def phase = new ValidateMarkersPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        1 * diagnostics.error(_, _, _, _)
    }

    def 'multiple unrealised seeds emit multiple errors'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seed1 = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def seed2 = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.age')), scope, Optional.empty())
        graph.addNode(seed1)
        graph.addNode(seed2)
        def directive1 = Mock(AnnotationMirror)
        def directive2 = Mock(AnnotationMirror)
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addEdge(Edge.seed(sourceRoot, seed1, directive1))
        graph.addEdge(Edge.seed(sourceRoot, seed2, directive2))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidateMarkersPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        2 * diagnostics.error(_, _, _, _)
    }

    def 'seed node with markers passes'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seedNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def realisedNode = new Node(Optional.of(mockTypeMirror('String')), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        graph.addNode(seedNode)
        graph.addNode(realisedNode)
        graph.addEdge(Edge.marker(seedNode, realisedNode, 'GetterRead'))
        def directive = Mock(AnnotationMirror)
        def sourceRoot = new Node(Optional.of(mockTypeMirror('Person')), new SourceLocation(mockAccessPath('person')), scope, Optional.empty())
        graph.addNode(sourceRoot)
        graph.addEdge(Edge.seed(sourceRoot, seedNode, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidateMarkersPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        0 * diagnostics.error(_, _, _, _)
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

    private TypeElement mockTypeElement() {
        def m = Mock(TypeElement)
        m.qualifiedName >> "test.Mapper"
        m
    }
}
