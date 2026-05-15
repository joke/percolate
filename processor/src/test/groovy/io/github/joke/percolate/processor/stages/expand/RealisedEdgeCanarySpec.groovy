package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.stages.expand.properties.fakes.IdentityBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.GraphFixtures
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class RealisedEdgeCanarySpec extends Specification {

    def 'identity bridge produces realised edge'() {
        given:
        final seed = GraphFixtures.graphWithSeedAndRealisedPath()
        final bridge = new IdentityBridge(TypeUniverse.STRING, TypeUniverse.STRING)

        when:
        final result = ExpansionHarness.expand(seed, [bridge], [], [])

        then:
        result.expandedGraph().edges().any { it.kind == EdgeKind.REALISED }
    }
}
