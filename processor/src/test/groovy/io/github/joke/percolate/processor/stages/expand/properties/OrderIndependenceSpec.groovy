package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class OrderIndependenceSpec extends Specification {

    def 'permuting strategies does not change output for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        final bridgesReversed = bridges.reverse()
        final sourceStepsReversed = sourceSteps.reverse()
        final groupTargetsReversed = groupTargets.reverse()

        when:
        final original = expand(graph, bridges, sourceSteps, groupTargets)
        final permuted = expand(graph, bridgesReversed, sourceStepsReversed, groupTargetsReversed)

        then:
        nodeIds(permuted.expandedGraph()) == nodeIds(original.expandedGraph())
        edgeTuples(permuted.expandedGraph()) == edgeTuples(original.expandedGraph())
    }
}
