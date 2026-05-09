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

import javax.annotation.processing.Messager

@Tag('unit')
class ExpandStageBudgetSpec extends Specification {

    def 'expansion terminates when edgeCount stops changing'() {
        given:
        def graph = new MapperGraph()
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
        1 * phase1.apply(_)
        1 * phase2.apply(_)
        1 * phase3.apply(_)
        0 * diagnostics.error(_, { it != null && it.contains('converge') })
    }

    def 'round cap emits error when expansion does not converge'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def seedNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('seed')), scope, Optional.empty())
        graph.addNode(seedNode)
        def targetNode = new Node(Optional.empty(), new SourceLocation(mockAccessPath('target')), scope, Optional.empty())
        graph.addNode(targetNode)
        def directive = Mock(AnnotationMirror)
        graph.addEdge(Edge.seed(seedNode, targetNode, directive))

        def ctx = Mock(MapperContext)
        ctx.getGraph() >> graph
        def typeElement = Mock(TypeElement)
        ctx.getMapperType() >> typeElement

        // Phase that always adds edges to prevent convergence
        def mutatingPhase = new NeverConvergingPhase(scope)
        def phase1 = Mock(ExpansionPhase)
        def phase2 = Mock(ExpansionPhase)
        def messager = Mock(Messager)
        def diagnostics = Spy(Diagnostics, constructorArgs: [messager])
        def stage = new ExpandStage([mutatingPhase, phase1, phase2], diagnostics)

        when:
        stage.run(ctx)

        then:
        1 * diagnostics.error(typeElement, { it != null && it.contains('converge') })
        diagnostics.hasErrorsFor(typeElement)
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

    private static class NeverConvergingPhase implements ExpansionPhase {
        private final MethodScope scope
        private int counter = 0

        NeverConvergingPhase(final MethodScope scope) {
            this.scope = scope
        }

        @Override
        void apply(final MapperGraph graph) {
            counter++
            def loc1 = new SourceLocation(new AccessPath(['sub' + counter + 'a']))
            def loc2 = new SourceLocation(new AccessPath(['sub' + counter + 'b']))
            def node1 = new Node(Optional.empty(), loc1, scope, Optional.empty())
            def node2 = new Node(Optional.empty(), loc2, scope, Optional.empty())
            graph.addNode(node1)
            graph.addNode(node2)
            graph.addEdge(Edge.subSeed(node1, node2, 'test', Optional.empty()))
        }
    }
}
