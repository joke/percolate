package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds
import static io.github.joke.percolate.processor.test.GraphCompare.union

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

@Tag('unit')
@Timeout(30)
class DisjointAdditivitySpec extends Specification {

    def 'expanding union equals unioning expansions for random inputs'() {
        given:
        final graphA = GraphGenerator.randomSeed()
        final graphB = GraphGenerator.randomSeed()
        final bridges = GraphGenerator.randomBridges()
        final sourceSteps = GraphGenerator.randomSourceSteps()
        final groupTargets = GraphGenerator.randomGroupTargets()

        when:
        final combinedGraph = union(graphA, graphB)
        final expandedUnion = expand(combinedGraph, bridges, sourceSteps, groupTargets)
        final expandedA = expand(graphA, bridges, sourceSteps, groupTargets)
        final expandedB = expand(graphB, bridges, sourceSteps, groupTargets)
        final unionOfExpansions = union(expandedA.expandedGraph(), expandedB.expandedGraph())

        then:
        nodeIds(expandedUnion.expandedGraph()) == nodeIds(unionOfExpansions)
        edgeTuples(expandedUnion.expandedGraph()) == edgeTuples(unionOfExpansions)
    }
}
