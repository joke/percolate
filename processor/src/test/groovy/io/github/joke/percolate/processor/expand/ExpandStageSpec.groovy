package io.github.joke.percolate.processor.expand

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.EdgeKind
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class ExpandStageSpec extends Specification {

    def 'runs three phases in order'() {
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
        1 * phase1.apply(graph)
        1 * phase2.apply(graph)
        1 * phase3.apply(graph)
    }

    def 'each phase runs exactly once'() {
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
    }

    def 'no SUB_SEED edges in v1 demo'() {
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
        def subSeedEdges = graph.edges().filter { it.kind == EdgeKind.SUB_SEED }.toList()
        subSeedEdges.isEmpty()
    }

    def 'no cycles in v1 demo'() {
        given:
        def graph = new MapperGraph()
        graph.graph.isCycleFound() >> false
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
        0 * diagnostics.error(_, _)
        0 * diagnostics.error(_, _, _, _)
    }

    def 'no budget exhaustion in v1 demo'() {
        given:
        def graph = new MapperGraph()
        graph.graph.isCycleFound() >> false
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
        0 * diagnostics.error(_, _, _, { it != null && it.contains('budget') })
    }
}
