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
class IdempotenceProperty extends PropertyTestBase {

    @Property(seed = "1002")
    void secondExpansionAddsNothing(
            @ForAll("mapperSpecs") final MapperSpec spec, @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var firstPass = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var secondPass = ExpansionHarness.expand(
                firstPass.expandedGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());

        assertThat(GraphCompare.nodeIds(secondPass.expandedGraph()))
                .isEqualTo(GraphCompare.nodeIds(firstPass.expandedGraph()));
        assertThat(GraphCompare.edgeTuples(secondPass.expandedGraph()))
                .isEqualTo(GraphCompare.edgeTuples(firstPass.expandedGraph()));
    }
}
