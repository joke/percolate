package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joke.percolate.test.MapperSpec.ArgSpec;
import io.github.joke.percolate.test.MapperSpec.DirectiveSpec;
import io.github.joke.percolate.test.MapperSpec.MethodSpec;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MapperSpecTest {

    @Test
    void singleMethodWithNoDirectivesBuildsGraphWithArgs() {
        final var spec = new MapperSpec(List.of(
                new MethodSpec("m", List.of(new ArgSpec("a", TypeUniverse.INT)), TypeUniverse.LONG, List.of())));
        final var graph = spec.toGraph();
        assertThat(graph.nodeCount()).isPositive();
    }

    @Test
    void singleMethodWithMatchingDirectiveBuildsEdge() {
        final var spec = new MapperSpec(List.of(new MethodSpec(
                "m",
                List.of(new ArgSpec("a", TypeUniverse.STRING)),
                TypeUniverse.STRING,
                List.of(new DirectiveSpec("out", "a")))));
        assertThat(spec.toGraph().edgeCount()).isPositive();
    }

    @Test
    void multipleMethodsBuildIndependentScopes() {
        final var spec = new MapperSpec(List.of(
                new MethodSpec("m1", List.of(new ArgSpec("x", TypeUniverse.INT)), TypeUniverse.LONG, List.of()),
                new MethodSpec("m2", List.of(new ArgSpec("y", TypeUniverse.STRING)), TypeUniverse.STRING, List.of())));
        final var graph = spec.toGraph();
        assertThat(graph.nodeCount()).isGreaterThan(2);
    }

    @Test
    void methodWithDistinctPathDirectiveCreatesExtraSourceNode() {
        final var spec = new MapperSpec(List.of(new MethodSpec(
                "m",
                List.of(new ArgSpec("p", TypeUniverse.STRING)),
                TypeUniverse.STRING,
                List.of(new DirectiveSpec("out", "p.nested")))));
        assertThat(spec.toGraph().nodeCount()).isGreaterThan(2);
    }

    @Test
    void emptyMethodsListProducesEmptyGraph() {
        assertThat(new MapperSpec(List.of()).toGraph().nodeCount()).isZero();
    }
}
