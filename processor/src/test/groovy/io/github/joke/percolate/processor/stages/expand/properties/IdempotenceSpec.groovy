package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class IdempotenceSpec extends Specification {

    def 'second expansion adds nothing for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        when:
        final firstPass = expand(graph, bridges, sourceSteps, groupTargets)
        final secondPass = expand(firstPass.expandedGraph(), bridges, sourceSteps, groupTargets)

        then:
        nodeIds(secondPass.expandedGraph()) == nodeIds(firstPass.expandedGraph())
        edgeTuples(secondPass.expandedGraph()) == edgeTuples(firstPass.expandedGraph())
    }
}
