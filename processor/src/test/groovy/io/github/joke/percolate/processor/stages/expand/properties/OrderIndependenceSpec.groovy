package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

class OrderIndependenceSpec extends ExpansionPropertyBase {

    @Property(seed = '8888')
    void 'permuting strategies does not change output'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final bridgesReversed      = bridges.reverse()
        final groupTargetsReversed = targets.reverse()

        final original   = expand(graph, bridges, targets)
        final permuted   = expand(graph, bridgesReversed, groupTargetsReversed)

        assert nodeIds(permuted.expandedGraph()) == nodeIds(original.expandedGraph())
        assert edgeTuples(permuted.expandedGraph()) == edgeTuples(original.expandedGraph())
    }
}
