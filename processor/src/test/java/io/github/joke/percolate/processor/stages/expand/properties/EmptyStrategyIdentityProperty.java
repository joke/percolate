package io.github.joke.percolate.processor.stages.expand.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.ExpansionHarness;
import io.github.joke.percolate.test.GraphCompare;
import io.github.joke.percolate.test.MapperSpec;
import io.github.joke.percolate.test.PropertyTestBase;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("unit")
class EmptyStrategyIdentityProperty extends PropertyTestBase {

    @Property(seed = "1007")
    void emptyStrategySetPreservesSeed(@ForAll("mapperSpecs") final MapperSpec spec) {
        final var seed = spec.toGraph();
        final var result = ExpansionHarness.expand(seed, List.of(), List.of(), List.of());

        assertThat(GraphCompare.nodeIds(result.expandedGraph())).isEqualTo(GraphCompare.nodeIds(seed));
        assertThat(GraphCompare.edgeTuples(result.expandedGraph())).isEqualTo(GraphCompare.edgeTuples(seed));
    }
}
