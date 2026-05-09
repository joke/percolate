package io.github.joke.percolate.processor.stages.expand
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.Diagnostics

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
class ExpandStageBudgetSpec extends Specification {

    def 'budget exceeded after 100 expansions emits error'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))

        // Create a seed node
        def seedNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('seed')), scope, Optional.empty())
        graph.addNode(seedNode)
        def directive = Mock(AnnotationMirror)
        def seedEdge = Edge.seed(seedNode, new Node(Optional.empty(), new SourceLocation(mockAccessPath('target')), scope, Optional.empty()), directive)
        graph.addEdge(seedEdge)

        // Add 101 SUB_SEED edges from the seed to exceed budget
        for (int i = 0; i < 101; i++) {
            def subNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('sub' + i)), scope, Optional.empty())
            graph.addNode(subNode)
            graph.addEdge(Edge.subSeed(seedNode, subNode, 'test.Strategy', Optional.empty()))
        }

        def ctx = Mock(MapperContext)
        ctx.getGraph() >> graph
        def typeElement = Mock(TypeElement)
        ctx.getMapperType() >> typeElement

        def phase1 = Mock(ExpansionPhase)
        def phase2 = Mock(ExpansionPhase)
        def phase3 = Mock(ExpansionPhase)
        phase1.apply(_) >> true
        phase2.apply(_) >> true
        phase3.apply(_) >> true
        def diagnostics = Mock(Diagnostics)
        def stage = new ExpandStage([phase1, phase2, phase3], diagnostics)

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.error(_, directive, null, { it != null && it.contains('budget') })
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
