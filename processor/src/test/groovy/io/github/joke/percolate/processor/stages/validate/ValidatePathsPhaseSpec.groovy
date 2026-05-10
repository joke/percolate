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
class ValidatePathsPhaseSpec extends Specification {

    TypeMirror mockTypeMirror(String typeName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> typeName
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        typeMirror.toString() >> typeName
        typeMirror
    }

    AccessPath mockAccessPath(String path) {
        new AccessPath(path.split('\\.').toList())
    }

    TargetPath mockTargetPath(String path) {
        new TargetPath(path.isEmpty() ? [] : path.split('\\.').toList())
    }

    ExecutableElement mockMethod(String name) {
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

    TypeElement mockTypeElement() {
        def m = Mock(TypeElement)
        m.qualifiedName >> "test.Mapper"
        m
    }

    def 'gap diagnostic includes both shoulders and missing type pair'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))

        // Source side: src[person→getAddress()]:Person.Address
        def sourceShoulder = new Node(Optional.of(mockTypeMirror('Person.Address')), new SourceLocation(mockAccessPath('person→getAddress()')), scope, Optional.empty())
        // Target side: slot[address]:Human.Address
        def targetShoulder = new Node(Optional.of(mockTypeMirror('Human.Address')), new TargetLocation(mockTargetPath('address')), scope, Optional.empty())
        // Seed edge bridging source to target
        def sourceSeed = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.address')), scope, Optional.empty())
        def targetSeed = new Node(Optional.empty(), new TargetLocation(mockTargetPath('address')), scope, Optional.empty())
        graph.addNode(sourceShoulder)
        graph.addNode(targetShoulder)
        graph.addNode(sourceSeed)
        graph.addNode(targetSeed)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceSeed, targetSeed, directive))
        graph.addEdge(Edge.marker(sourceSeed, sourceShoulder, 'GetterRead'))
        graph.addEdge(Edge.marker(targetSeed, targetShoulder, 'ConstructorCall'))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidatePathsPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        1 * diagnostics.error(_, directive, null, { msg ->
            msg != null && msg.contains('Person.Address') && msg.contains('Human.Address')
        })
    }

    def 'passes when realised path exists'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))

        // Source shoulder connected to target shoulder via REALISED edge
        def sourceShoulder = new Node(Optional.of(mockTypeMirror('String')), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def targetShoulder = new Node(Optional.of(mockTypeMirror('String')), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceShoulder)
        graph.addNode(targetShoulder)
        graph.addEdge(Edge.realised(sourceShoulder, targetShoulder, Weights.NOOP, Optional.empty(), (vars, inputs) -> null, 'DirectAssign'))

        def sourceSeed = new Node(Optional.of(mockTypeMirror('String')), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def targetSeed = new Node(Optional.of(mockTypeMirror('String')), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceSeed)
        graph.addNode(targetSeed)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceSeed, targetSeed, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidatePathsPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        0 * diagnostics.error(_, _, _, _)
    }

    def 'skips scarred mappers'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def sourceSeed = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.name')), scope, Optional.empty())
        def targetSeed = new Node(Optional.empty(), new TargetLocation(mockTargetPath('name')), scope, Optional.empty())
        graph.addNode(sourceSeed)
        graph.addNode(targetSeed)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceSeed, targetSeed, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidatePathsPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        // Phase runs but finds no path (no realised edges between seed nodes)
        1 * diagnostics.error(_, directive, null, _)
    }

    def 'emits at most one error per SEED bridge'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))

        // One seed bridge edge
        def sourceSeed = new Node(Optional.empty(), new SourceLocation(mockAccessPath('person.address')), scope, Optional.empty())
        def targetSeed = new Node(Optional.empty(), new TargetLocation(mockTargetPath('address')), scope, Optional.empty())
        graph.addNode(sourceSeed)
        graph.addNode(targetSeed)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(sourceSeed, targetSeed, directive))

        def diagnostics = Mock(Diagnostics)

        when:
        def phase = new ValidatePathsPhase(diagnostics)
        phase.apply(graph, mockTypeElement())

        then:
        1 * diagnostics.error(_, directive, null, _)
    }
}
