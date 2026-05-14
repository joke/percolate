package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.processor.graph.Node;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SeedDslTest {

    @Test
    void buildsGraphWithNodes() {
        final var graph = simpleSeed().build();
        assertThat(graph.nodeCount()).isPositive();
    }

    @Test
    void buildsGraphWithEdges() {
        final var graph = simpleSeed().build();
        assertThat(graph.edgeCount()).isPositive();
    }

    @Test
    void nodeIdentityCollapsesWithinSameScope() {
        final var graph = simpleSeed().build();
        final var ids = graph.nodes().map(Node::id).collect(Collectors.toUnmodifiableSet());
        assertThat(ids).hasSize(graph.nodeCount());
    }

    @Test
    void independentBuildsProduceConsistentShape() {
        final var dsl1 = SeedDsl.seed();
        final var m1 = dsl1.method("a");
        m1.arg("x", TypeUniverse.INT).returns(TypeUniverse.LONG);
        m1.directive(m1.target("out"), m1.source("x"));
        final var dsl2 = SeedDsl.seed();
        final var m2 = dsl2.method("b");
        m2.arg("x", TypeUniverse.INT).returns(TypeUniverse.LONG);
        m2.directive(m2.target("out"), m2.source("x"));
        assertThat(dsl1.build().nodeCount()).isEqualTo(dsl2.build().nodeCount());
    }

    @Test
    void directiveWithSubSegmentCreatesSourceNode() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("nested");
        method.arg("p", TypeUniverse.STRING);
        method.returns(TypeUniverse.STRING);
        method.directive(method.target("out"), method.source("p.subField"));
        final var graph = dsl.build();
        assertThat(graph.nodeCount()).isGreaterThan(1);
    }

    @Test
    void methodWithoutReturnTypeStillBuilds() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("voidLike");
        method.arg("x", TypeUniverse.INT);
        final var graph = dsl.build();
        assertThat(graph.nodeCount()).isPositive();
    }

    @Test
    void emptyMethodBuildsEmptyGraph() {
        final var graph = SeedDsl.seed().build();
        assertThat(graph.nodeCount()).isZero();
    }

    private SeedDsl simpleSeed() {
        final var dsl = SeedDsl.seed();
        final var method = dsl.method("map");
        method.arg("input", TypeUniverse.STRING);
        method.returns(TypeUniverse.INT);
        method.directive(method.target("output"), method.source("input"));
        return dsl;
    }
}
