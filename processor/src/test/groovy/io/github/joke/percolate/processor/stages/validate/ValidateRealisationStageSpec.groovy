package io.github.joke.percolate.processor.stages.validate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class ValidateRealisationStageSpec extends Specification {

    def 'Tier-2 runs first, Tier-3 runs after'() {
        given:
        def ctx = Mock(MapperContext)
        def typeElement = Mock(TypeElement)
        def graph = Mock(io.github.joke.percolate.processor.graph.MapperGraph)
        ctx.getMapperType() >> typeElement
        ctx.getGraph() >> graph
        ctx.isScarred(_) >> false

        def markersPhase = Mock(ValidateMarkersPhase)
        def pathsPhase = Mock(ValidatePathsPhase)
        def diagnostics = Mock(Diagnostics)
        def stage = new ValidateRealisationStage(markersPhase, pathsPhase, diagnostics)

        when:
        stage.run(ctx)

        then:
        1 * markersPhase.apply(graph, typeElement)
        1 * pathsPhase.apply(graph, typeElement)
    }

    def 'scarring from Tier-2 suppresses Tier-3 for that mapper'() {
        given:
        def ctx = Mock(MapperContext)
        def typeElement = Mock(TypeElement)
        def graph = Mock(io.github.joke.percolate.processor.graph.MapperGraph)
        ctx.getMapperType() >> typeElement
        ctx.getGraph() >> graph

        def markersPhase = Mock(ValidateMarkersPhase)
        def pathsPhase = Mock(ValidatePathsPhase)
        def diagnostics = Mock(Diagnostics)

        // Tier-2 scars the mapper
        ctx.isScarred(diagnostics) >> false >> true

        def stage = new ValidateRealisationStage(markersPhase, pathsPhase, diagnostics)

        when:
        stage.run(ctx)

        then:
        1 * markersPhase.apply(graph, typeElement)
        0 * pathsPhase.apply(_, _)
    }
}
