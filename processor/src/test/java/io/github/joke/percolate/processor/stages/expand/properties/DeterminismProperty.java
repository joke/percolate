package io.github.joke.percolate.processor.stages.expand.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.ExpansionHarness;
import io.github.joke.percolate.test.GraphCompare;
import io.github.joke.percolate.test.MapperSpec;
import io.github.joke.percolate.test.PropertyTestBase;
import io.github.joke.percolate.test.StrategyBundle;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("unit")
class DeterminismProperty extends PropertyTestBase {

    @Property(seed = "1001")
    void expansionIsDeterministic(
            @ForAll("mapperSpecs") final MapperSpec spec, @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var first = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var second = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());

        assertThat(GraphCompare.nodeIds(first.expandedGraph())).isEqualTo(GraphCompare.nodeIds(second.expandedGraph()));
        assertThat(GraphCompare.edgeTuples(first.expandedGraph()))
                .isEqualTo(GraphCompare.edgeTuples(second.expandedGraph()));
    }
}
