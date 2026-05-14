package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ExpansionHarnessTest {

    @Test
    void spiModeReturnsResult() {
        final var graph = simpleSeed();
        final var result = ExpansionHarness.expand(graph);
        assertThat(result).isNotNull();
    }

    @Test
    void explicitModeWithEmptyStrategiesIsIdentity() {
        final var graph = simpleSeed();
        final var result = ExpansionHarness.expand(graph, List.of(), List.of(), List.of());
        assertThat(result.expandedGraph().nodeCount()).isEqualTo(graph.nodeCount());
    }

    @Test
    void explicitModeProducesNonNullDiagnostics() {
        final var result = ExpansionHarness.expand(simpleSeed(), List.of(), List.of(), List.of());
        assertThat(result.diagnostics()).isNotNull();
    }

    @Test
    void explicitModeProducesNonNegativeRoundCount() {
        final var result = ExpansionHarness.expand(simpleSeed(), List.of(), List.of(), List.of());
        assertThat(result.roundCount()).isNotNegative();
    }

    private static io.github.joke.percolate.processor.graph.MapperGraph simpleSeed() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("m");
        method.arg("x", TypeUniverse.INT).returns(TypeUniverse.INT);
        method.directive(method.target("out"), method.source("x"));
        return dsl.build();
    }
}
