package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.test.ExpansionAssertions.ExpansionAssert.DiagnosticKind;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ExpansionAssertionsTest {

    @Test
    void noPathDiagnosticMatchesUpperCase() {
        assertThatThrownBy(() -> ExpansionAssertions.assertThat(noErrors()).reportedError(DiagnosticKind.NO_PATH))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void noPathDiagnosticPassesWhenMessagePresent() {
        ExpansionAssertions.assertThat(withDiagnostic("No realised path here")).reportedError(DiagnosticKind.NO_PATH);
    }

    @Test
    void cycleDiagnosticMatchesKeyword() {
        ExpansionAssertions.assertThat(withDiagnostic("Cycle detected at scope"))
                .reportedError(DiagnosticKind.CYCLE);
    }

    @Test
    void roundCapDiagnosticMatchesConvergeKeyword() {
        ExpansionAssertions.assertThat(withDiagnostic("did not converge")).reportedError(DiagnosticKind.ROUND_CAP);
    }

    @Test
    void reachableFailsWhenSourceMissing() {
        assertThatThrownBy(() -> ExpansionAssertions.assertThat(noErrors()).reachable("missingSource", "missingTarget"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void chainForSeedEdgeFailsWhenNoMatchingMessage() {
        assertThatThrownBy(() -> ExpansionAssertions.assertThat(withDiagnostic("No realised path between A and B"))
                        .reportedError(DiagnosticKind.NO_PATH)
                        .forSeedEdge("X", "Y"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void chainForSeedEdgePassesWhenMessageContainsEndpoint() {
        ExpansionAssertions.assertThat(withDiagnostic("No realised path between SOURCE and TARGET"))
                .reportedError(DiagnosticKind.NO_PATH)
                .forSeedEdge("SOURCE", "TARGET");
    }

    @Test
    void noPathDiagnosticMatchesLowercaseVariant() {
        ExpansionAssertions.assertThat(withDiagnostic("no realised path here")).reportedError(DiagnosticKind.NO_PATH);
    }

    @Test
    void cycleDiagnosticMatchesLowercaseVariant() {
        ExpansionAssertions.assertThat(withDiagnostic("oh look, a cycle")).reportedError(DiagnosticKind.CYCLE);
    }

    @Test
    void roundCapDiagnosticMatchesRoundKeyword() {
        ExpansionAssertions.assertThat(withDiagnostic("exceeded round budget")).reportedError(DiagnosticKind.ROUND_CAP);
    }

    @Test
    void reachableSucceedsWithRealisedPath() {
        final var graph = GraphFixtures.graphWithSeedAndRealisedPath();
        final var result = ExpansionResult.of(graph, List.of(), 1, true, placeholderType());
        ExpansionAssertions.assertThat(result).reachable("in", "out");
    }

    @Test
    void reachableFailsWhenNoRealisedEdge() {
        assertThatThrownBy(() -> ExpansionAssertions.assertThat(noErrors()).reachable("nothing", "nowhere"))
                .isInstanceOf(AssertionError.class);
    }

    private static ExpansionResult noErrors() {
        return ExpansionResult.of(new MapperGraph(), List.of(), 0, true, placeholderType());
    }

    private static ExpansionResult withDiagnostic(final String message) {
        return ExpansionResult.of(new MapperGraph(), List.of(message), 0, false, placeholderType());
    }

    private static javax.lang.model.element.TypeElement placeholderType() {
        return java.util.Objects.requireNonNull(TypeUniverse.elements().getTypeElement("java.lang.Object"));
    }
}
