package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

class IdempotenceSpec extends ExpansionPropertyBase {

    @Property(seed = '7777')
    void 'second expansion adds nothing'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final firstPass  = expand(graph, bridges, targets)
        final secondPass = expand(firstPass.expandedGraph(), bridges, targets)
        assert nodeIds(secondPass.expandedGraph()) == nodeIds(firstPass.expandedGraph())
        assert edgeTuples(secondPass.expandedGraph()) == edgeTuples(firstPass.expandedGraph())
    }
}
