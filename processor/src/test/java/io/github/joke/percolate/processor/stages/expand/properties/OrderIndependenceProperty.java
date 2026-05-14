package io.github.joke.percolate.processor.stages.expand.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.ExpansionHarness;
import io.github.joke.percolate.test.GraphCompare;
import io.github.joke.percolate.test.MapperSpec;
import io.github.joke.percolate.test.PropertyTestBase;
import io.github.joke.percolate.test.StrategyBundle;
import java.util.ArrayList;
import java.util.Collections;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("unit")
class OrderIndependenceProperty extends PropertyTestBase {

    @Property(seed = "1003")
    void permutingStrategiesDoesNotChangeOutput(
            @ForAll("mapperSpecs") final MapperSpec spec, @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var bridgesReversed = new ArrayList<>(bundle.getBridges());
        Collections.reverse(bridgesReversed);
        final var sourceStepsReversed = new ArrayList<>(bundle.getSourceSteps());
        Collections.reverse(sourceStepsReversed);
        final var groupTargetsReversed = new ArrayList<>(bundle.getGroupTargets());
        Collections.reverse(groupTargetsReversed);

        final var original = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var permuted =
                ExpansionHarness.expand(spec.toGraph(), bridgesReversed, sourceStepsReversed, groupTargetsReversed);

        assertThat(GraphCompare.nodeIds(permuted.expandedGraph()))
                .isEqualTo(GraphCompare.nodeIds(original.expandedGraph()));
        assertThat(GraphCompare.edgeTuples(permuted.expandedGraph()))
                .isEqualTo(GraphCompare.edgeTuples(original.expandedGraph()));
    }
}
