package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.SourceStep
import net.jqwik.api.ForAll
import net.jqwik.api.Property

import static io.github.joke.percolate.processor.test.ExpansionHarness.expand

class IdentityCollapseSpec extends ExpansionPropertyBase {

    @Property(seed = '1111')
    void 'no two nodes share scope-location-type'(
            @ForAll('seedGraphs') MapperGraph graph,
            @ForAll('fakeBridges') List<Bridge> bridges,
            @ForAll('fakeSourceSteps') List<SourceStep> sources,
            @ForAll('fakeGroupTargets') List<GroupTarget> targets) {
        final result = expand(graph, bridges, sources, targets)
        assert !result.hasIdentityCollisions()
    }
}
