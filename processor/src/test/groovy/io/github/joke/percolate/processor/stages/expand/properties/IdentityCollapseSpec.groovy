package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class IdentityCollapseSpec extends Specification {

    def 'no two nodes share scope-location-type for random inputs'() {
        given:
        final graph = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        when:
        final result = expand(graph, bridges, sourceSteps, groupTargets)

        then:
        !result.hasIdentityCollisions()
    }
}
