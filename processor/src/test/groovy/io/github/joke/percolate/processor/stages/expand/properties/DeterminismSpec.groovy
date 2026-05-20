package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

class DeterminismSpec extends ExpansionPropertyBase {

    @Property(seed = '4242')
    void 'expansion is deterministic'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final first  = expand(graph, bridges, targets)
        final second = expand(graph, bridges, targets)
        assert nodeIds(first.expandedGraph())   == nodeIds(second.expandedGraph())
        assert edgeTuples(first.expandedGraph()) == edgeTuples(second.expandedGraph())
    }
}
