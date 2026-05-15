package io.github.joke.percolate.processor.stages.expand.properties

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand
import static io.github.joke.percolate.processor.test.GraphCompare.edgeTuples
import static io.github.joke.percolate.processor.test.GraphCompare.nodeIds

import net.jqwik.api.ForAll
import net.jqwik.api.Property

import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.GroupTarget
import io.github.joke.percolate.processor.spi.SourceStep
import io.github.joke.percolate.processor.graph.MapperGraph

class IdempotenceSpec extends ExpansionPropertyBase {

    @Property(seed = '7777')
    void 'second expansion adds nothing'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeSourceSteps') List<SourceStep> sources,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final firstPass  = expand(graph, bridges, sources, targets)
        final secondPass = expand(firstPass.expandedGraph(), bridges, sources, targets)
        assert nodeIds(secondPass.expandedGraph()) == nodeIds(firstPass.expandedGraph())
        assert edgeTuples(secondPass.expandedGraph()) == edgeTuples(firstPass.expandedGraph())
    }
}
