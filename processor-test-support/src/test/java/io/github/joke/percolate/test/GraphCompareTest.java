package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.processor.graph.MapperGraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class GraphCompareTest {

    @Test
    void emptyGraphHasNoNodeIds() {
        assertThat(GraphCompare.nodeIds(new MapperGraph())).isEmpty();
    }

    @Test
    void emptyGraphHasNoEdgeTuples() {
        assertThat(GraphCompare.edgeTuples(new MapperGraph())).isEmpty();
    }

    @Test
    void unionOfTwoEmptyGraphsIsEmpty() {
        assertThat(GraphCompare.union(new MapperGraph(), new MapperGraph()).nodeCount())
                .isZero();
    }

    @Test
    void unionPreservesNonEmptySource() {
        final var seed = SeedDsl.seed();
        final var method = seed.method("m");
        method.arg("x", TypeUniverse.INT).returns(TypeUniverse.INT);
        method.directive(method.target("out"), method.source("x"));
        final var graphA = seed.build();
        final var combined = GraphCompare.union(graphA, new MapperGraph());
        assertThat(combined.nodeCount()).isEqualTo(graphA.nodeCount());
    }
}
