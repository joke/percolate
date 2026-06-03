package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ElementScope;
import java.util.Comparator;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(exclude = {"codegen", "elementScope", "strategyClassFqn"})
public class Edge implements Comparable<Edge> {

    private static final Comparator<Edge> EDGE_ORDER = Comparator.<Edge, String>comparing(e -> e.from.id())
            .thenComparing(e -> e.to.id())
            .thenComparingInt(e -> e.weight)
            .thenComparing(e -> e.kind)
            .thenComparing(e -> e.directive.isPresent());
    Node from;
    Node to;
    int weight;
    EdgeKind kind;
    Optional<AnnotationMirror> directive;
    Optional<Codegen> codegen;
    /**
     * The element-scope crossing of a container edge ({@code ENTERING} / {@code EXITING}), or empty for a scalar
     * (scope-preserving) edge. Persisted from the producing {@link io.github.joke.percolate.spi.ExpansionStep}'s
     * {@link ElementScope}; consumed by code-generation to weave the container operation.
     */
    Optional<ElementScope> elementScope;

    Optional<String> strategyClassFqn;

    Edge(
            final Node from,
            final Node to,
            final int weight,
            final EdgeKind kind,
            final Optional<AnnotationMirror> directive,
            final Optional<Codegen> codegen,
            final Optional<ElementScope> elementScope,
            final Optional<String> strategyClassFqn) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.kind = kind;
        this.directive = directive;
        this.codegen = codegen;
        this.elementScope = elementScope;
        this.strategyClassFqn = strategyClassFqn;
    }

    public static Edge seed(
            final Node from,
            final Node to,
            final Optional<AnnotationMirror> directive,
            final Optional<String> strategyClassFqn) {
        return new Edge(
                from,
                to,
                Weights.SENTINEL_UNREALISED,
                EdgeKind.SEED,
                directive,
                Optional.empty(),
                Optional.empty(),
                strategyClassFqn);
    }

    static Edge seedForTest(final Node from, final Node to) {
        return seed(from, to, Optional.empty(), Optional.empty());
    }

    static Edge copyWithEndpoints(final Edge original, final Node newFrom, final Node newTo) {
        return new Edge(
                newFrom,
                newTo,
                original.weight,
                original.kind,
                original.directive,
                original.codegen,
                original.elementScope,
                original.strategyClassFqn);
    }

    public static Edge realised(
            final Node from,
            final Node to,
            final int weight,
            final EdgeCodegen codegen,
            final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                Optional.of(codegen),
                Optional.empty(),
                Optional.of(strategyClassFqn));
    }

    public static Edge realised(
            final Node from,
            final Node to,
            final int weight,
            final Codegen provider,
            final ElementScope elementScope,
            final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                Optional.of(provider),
                Optional.of(elementScope),
                Optional.of(strategyClassFqn));
    }

    public static Edge marker(final Node from, final Node to, final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                Weights.NOOP,
                EdgeKind.MARKER,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(strategyClassFqn));
    }

    @Override
    public int compareTo(final Edge other) {
        return EDGE_ORDER.compare(this, other);
    }
}
