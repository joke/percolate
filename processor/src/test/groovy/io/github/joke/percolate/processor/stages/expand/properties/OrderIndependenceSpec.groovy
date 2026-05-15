package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import net.jqwik.api.ForAll
import net.jqwik.api.Property

import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.SourceStep
import io.github.joke.percolate.processor.graph.MapperGraph

class OrderIndependenceSpec extends ExpansionPropertyBase {

    @Property(seed = '8888')
    void 'permuting strategies does not change output'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeSourceSteps') List<SourceStep> sources,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final bridgesReversed      = bridges.reverse()
        final sourceStepsReversed  = sources.reverse()
        final groupTargetsReversed = targets.reverse()

        final original   = expand(graph, bridges, sources, targets)
        final permuted   = expand(graph, bridgesReversed, sourceStepsReversed, groupTargetsReversed)

        assert nodeIds(permuted.expandedGraph()) == nodeIds(original.expandedGraph())
        assert edgeTuples(permuted.expandedGraph()) == edgeTuples(original.expandedGraph())
    }
}
