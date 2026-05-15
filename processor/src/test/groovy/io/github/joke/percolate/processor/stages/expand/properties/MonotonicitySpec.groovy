package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class MonotonicitySpec extends Specification {

    def 'larger strategy set produces superset for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        when:
        final smaller = expand(graph, [], [], [])
        final larger = expand(graph, bridges, sourceSteps, groupTargets)

        then:
        edgeTuples(larger.expandedGraph()).containsAll(edgeTuples(smaller.expandedGraph()))
        nodeIds(larger.expandedGraph()).containsAll(nodeIds(smaller.expandedGraph()))
    }
}
