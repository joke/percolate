package io.github.joke.percolate.processor.stages.expand.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.ExpansionHarness;
import io.github.joke.percolate.test.MapperSpec;
import io.github.joke.percolate.test.PropertyTestBase;
import io.github.joke.percolate.test.StrategyBundle;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("unit")
class IdentityCollapseProperty extends PropertyTestBase {

    @Property(seed = "1005")
    void noTwoNodesShareScopeLocationType(
            @ForAll("mapperSpecs") final MapperSpec spec, @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var result = ExpansionHarness.expand(
                spec.toGraph(), bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());

        assertThat(result.hasIdentityCollisions()).isFalse();
    }
}
