package io.github.joke.percolate.processor.expand

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
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
import java.util.Optional

@Tag('unit')
class ExpandStageCycleSpec extends Specification {

    def 'cycle detected triggers error and mapper scarring'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))

        // Create a cycle: src[a] -> src[b] -> src[a] via SUB_SEED edges
        def nodeA = new Node(Optional.empty(), new SourceLocation(mockAccessPath('a')), scope, Optional.empty())
        def nodeB = new Node(Optional.empty(), new SourceLocation(mockAccessPath('b')), scope, Optional.empty())
        graph.addNode(nodeA)
        graph.addNode(nodeB)

        // Add SUB_SEED edges to create a cycle
        def subSeed1 = Edge.subSeed(nodeA, nodeB, 'test.Strategy')
        def subSeed2 = Edge.subSeed(nodeB, nodeA, 'test.Strategy')
        graph.addEdge(subSeed1)
        graph.addEdge(subSeed2)

        // Add a SEED edge to carry the directive
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(nodeA, nodeB, directive))

        def ctx = Mock(MapperContext)
        ctx.getGraph() >> graph
        def typeElement = Mock(TypeElement)
        ctx.getMapperType() >> typeElement

        def phase1 = Mock(ExpansionPhase)
        def phase2 = Mock(ExpansionPhase)
        def phase3 = Mock(ExpansionPhase)
        def diagnostics = Mock(Diagnostics)
        def stage = new ExpandStage([phase1, phase2, phase3], diagnostics)

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.error(_, directive, null, { it != null && it.contains('Cycle') })
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
