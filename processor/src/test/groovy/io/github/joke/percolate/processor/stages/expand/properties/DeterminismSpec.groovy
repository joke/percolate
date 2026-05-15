package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class DeterminismSpec extends Specification {

    def 'expansion is deterministic for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        when:
        final first = expand(graph, bridges, sourceSteps, groupTargets)
        final second = expand(graph, bridges, sourceSteps, groupTargets)

        then:
        nodeIds(first.expandedGraph()) == nodeIds(second.expandedGraph())
        edgeTuples(first.expandedGraph()) == edgeTuples(second.expandedGraph())
    }
}
