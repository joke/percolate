package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.EdgeKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SpiProbe {

    @Test
    void dumpExpansionForStringIdentity() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("map");
        method.arg("p", TypeUniverse.STRING).returns(TypeUniverse.STRING);
        method.directive(method.target("out"), method.source("p"));
        final var result = ExpansionHarness.expand(dsl.build());

        System.out.println("=== SPI probe: String → String identity ===");
        System.out.println("nodeCount=" + result.expandedGraph().nodeCount());
        System.out.println("edgeCount=" + result.expandedGraph().edgeCount());
        System.out.println("converged=" + result.converged());
        System.out.println("diagnostics=" + result.diagnostics());
        System.out.println("edge kinds:");
        result.expandedGraph().edges().forEach(e -> System.out.println("  " + e.getKind() + " " + e.getFrom().id()
                + " -> " + e.getTo().id() + " (strategy="
                + e.getStrategyClassFqn().orElse("?") + ")"));
        System.out.println("realised count = "
                + result.expandedGraph().edges().filter(x -> x.getKind() == EdgeKind.REALISED).count());
    }

    @Test
    void dumpExpansionForStringToInteger() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("convert");
        method.arg("p", TypeUniverse.STRING).returns(TypeUniverse.INTEGER);
        method.directive(method.target("out"), method.source("p"));
        final var result = ExpansionHarness.expand(dsl.build());

        System.out.println("=== SPI probe: String → Integer ===");
        System.out.println("converged=" + result.converged());
        System.out.println("diagnostics=" + result.diagnostics());
        System.out.println("realised count = "
                + result.expandedGraph().edges().filter(x -> x.getKind() == EdgeKind.REALISED).count());
    }
}
