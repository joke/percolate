package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.processor.graph.MapperGraph;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ExpansionResultTest {

    @Test
    void ofProducesResultWithNoFailure() {
        final var result = newResult(true);
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void failedProducesResultWithFailure() {
        final var result = ExpansionResult.failed(new MapperGraph(), List.of(), 0, "boom", placeholderType());
        assertThat(result.hasFailures()).isTrue();
    }

    @Test
    void failedExposesReason() {
        final var result = ExpansionResult.failed(new MapperGraph(), List.of(), 0, "boom", placeholderType());
        assertThat(result.failureReason()).isEqualTo("boom");
    }

    @Test
    void emptyDiagnosticsMeansNoErrors() {
        final var result = newResult(true);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void nonEmptyDiagnosticsMeansErrors() {
        final var result = ExpansionResult.of(new MapperGraph(), List.of("error"), 1, false, placeholderType());
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void convergedFlagSurfacesFromOf() {
        assertThat(newResult(true).converged()).isTrue();
    }

    @Test
    void notConvergedFlagSurfacesFromOf() {
        assertThat(newResult(false).converged()).isFalse();
    }

    @Test
    void roundCountSurfaces() {
        final var result = ExpansionResult.of(new MapperGraph(), List.of(), 5, true, placeholderType());
        assertThat(result.roundCount()).isEqualTo(5);
    }

    @Test
    void diagnosticsListIsImmutableCopy() {
        final var inputs = new java.util.ArrayList<>(List.of("a", "b"));
        final var result = ExpansionResult.of(new MapperGraph(), inputs, 0, true, placeholderType());
        inputs.clear();
        assertThat(result.diagnostics()).containsExactly("a", "b");
    }

    @Test
    void emptyGraphHasNoIdentityCollisions() {
        assertThat(newResult(true).hasIdentityCollisions()).isFalse();
    }

    @Test
    void emptyGraphHasNoOrphanRealisedNodes() {
        assertThat(newResult(true).hasOrphanRealisedNodes()).isFalse();
    }

    @Test
    void graphWithReachableRealisedEdgeHasNoOrphans() {
        final var graph = GraphFixtures.graphWithSeedAndRealisedPath();
        final var result = ExpansionResult.of(graph, List.of(), 1, true, placeholderType());
        assertThat(result.hasOrphanRealisedNodes()).isFalse();
    }

    @Test
    void graphWithOrphanRealisedEdgeIsFlagged() {
        final var graph = GraphFixtures.graphWithOrphanRealisedEdge();
        final var result = ExpansionResult.of(graph, List.of(), 1, true, placeholderType());
        assertThat(result.hasOrphanRealisedNodes()).isTrue();
    }

    @Test
    void dotRenderWithMapperTypeProducesNonEmpty() {
        assertThat(newResult(true).dotRender()).isNotEmpty();
    }

    @Test
    void isIdempotentReturnsTrueByContract() {
        assertThat(newResult(true).isIdempotent()).isTrue();
    }

    private static ExpansionResult newResult(final boolean converged) {
        return ExpansionResult.of(new MapperGraph(), List.of(), 1, converged, placeholderType());
    }

    private static javax.lang.model.element.TypeElement placeholderType() {
        return java.util.Objects.requireNonNull(TypeUniverse.elements().getTypeElement("java.lang.Object"));
    }
}
