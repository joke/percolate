package io.github.joke.percolate.processor.stages.expand.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.ExpansionHarness;
import io.github.joke.percolate.test.GraphCompare;
import io.github.joke.percolate.test.MapperSpec;
import io.github.joke.percolate.test.PropertyTestBase;
import io.github.joke.percolate.test.StrategyBundle;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("unit")
class MonotonicityProperty extends PropertyTestBase {

    @Property(seed = "1004")
    void largerStrategySetProducesSuperset(
            @ForAll("mapperSpecs") final MapperSpec spec, @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var smaller = ExpansionHarness.expand(spec.toGraph(), List.of(), List.of(), List.of());
        final var larger = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());

        assertThat(GraphCompare.edgeTuples(larger.expandedGraph()))
                .containsAll(GraphCompare.edgeTuples(smaller.expandedGraph()));
        assertThat(GraphCompare.nodeIds(larger.expandedGraph()))
                .containsAll(GraphCompare.nodeIds(smaller.expandedGraph()));
    }
}
