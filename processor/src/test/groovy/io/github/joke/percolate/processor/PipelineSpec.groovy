package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class PipelineSpec extends Specification {

    DiscoverAbstractMethods discoverAbstractMethods = Mock()
    DiscoverMappings discoverMappings = Mock()
    ValidateNoDuplicateTargets validateNoDuplicateTargets = Mock()
    ValidateSourceParameters validateSourceParameters = Mock()
    SeedGraph seedGraph = Mock()
    DumpGraph dumpGraph = Mock()

    Pipeline pipeline = new Pipeline(discoverAbstractMethods, discoverMappings, validateNoDuplicateTargets, validateSourceParameters, seedGraph, dumpGraph)

    def 'process invokes six stages in order'() {
        given:
        def typeElement = Mock(TypeElement)
        def shape = Mock(MapperShape)
        def mappings = Mock(MapperMappings)
        def graph = Mock(MapperGraph)

        when:
        pipeline.process(typeElement)

        then:
        1 * discoverAbstractMethods.apply(typeElement) >> shape
        1 * discoverMappings.apply(shape) >> mappings
        1 * validateNoDuplicateTargets.validate(mappings)
        1 * validateSourceParameters.validate(mappings)
        1 * seedGraph.apply(mappings) >> graph
        1 * dumpGraph.apply(graph, typeElement)
    }

    def 'a fresh MapperGraph is constructed per process invocation'() {
        given:
        def typeElement1 = Mock(TypeElement)
        def typeElement2 = Mock(TypeElement)
        def shape = Mock(MapperShape)
        def mappings = Mock(MapperMappings)
        def graph1 = Mock(MapperGraph)
        def graph2 = Mock(MapperGraph)

        and:
        2 * discoverAbstractMethods.apply(_) >> shape
        2 * discoverMappings.apply(_) >> mappings
        2 * validateNoDuplicateTargets.validate(_)
        2 * validateSourceParameters.validate(_)
        1 * seedGraph.apply(_) >> graph1
        1 * seedGraph.apply(_) >> graph2
        1 * dumpGraph.apply(graph1, typeElement1)
        1 * dumpGraph.apply(graph2, typeElement2)

        when:
        pipeline.process(typeElement1)
        pipeline.process(typeElement2)

        then:
        graph1 != graph2
    }
}
