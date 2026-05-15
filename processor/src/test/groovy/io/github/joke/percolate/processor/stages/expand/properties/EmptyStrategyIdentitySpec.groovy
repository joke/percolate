package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class EmptyStrategyIdentitySpec extends Specification {

    def 'empty strategy set preserves seed for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()

        when:
        final result = expand(graph, [], [], [])

        then:
        nodeIds(result.expandedGraph()) == nodeIds(graph)
        edgeTuples(result.expandedGraph()) == edgeTuples(graph)
    }
}
