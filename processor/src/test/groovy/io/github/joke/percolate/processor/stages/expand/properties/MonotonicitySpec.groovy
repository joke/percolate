package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.SourceStep
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

class MonotonicitySpec extends ExpansionPropertyBase {

    @Property(seed = '9999')
    void 'larger strategy set produces superset'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeSourceSteps') List<SourceStep> sources,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final smaller = expand(graph, [], [], [])
        final larger  = expand(graph, bridges, sources, targets)

        assert edgeTuples(larger.expandedGraph()).containsAll(edgeTuples(smaller.expandedGraph()))
        assert nodeIds(larger.expandedGraph()).containsAll(nodeIds(smaller.expandedGraph()))
    }
}
