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
class DisjointAdditivityProperty extends PropertyTestBase {

    @Property(seed = "1006")
    void expandingUnionEqualsUnioningExpansions(
            @ForAll("mapperSpecs") final MapperSpec specA,
            @ForAll("mapperSpecs") final MapperSpec specB,
            @ForAll("strategyBundles") final StrategyBundle bundle) {
        final var graphA = specA.toGraph();
        final var graphB = specB.toGraph();
        final var union = GraphCompare.union(graphA, graphB);

        final var expandedUnion =
                ExpansionHarness.expand(union, bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var expandedA =
                ExpansionHarness.expand(graphA, bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var expandedB =
                ExpansionHarness.expand(graphB, bundle.getBridges(), bundle.getSourceSteps(), bundle.getGroupTargets());
        final var unionOfExpansions = GraphCompare.union(expandedA.expandedGraph(), expandedB.expandedGraph());

        assertThat(GraphCompare.nodeIds(expandedUnion.expandedGraph()))
                .isEqualTo(GraphCompare.nodeIds(unionOfExpansions));
        assertThat(GraphCompare.edgeTuples(expandedUnion.expandedGraph()))
                .isEqualTo(GraphCompare.edgeTuples(unionOfExpansions));
    }
}
