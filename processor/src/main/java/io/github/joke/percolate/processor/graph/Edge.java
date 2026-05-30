package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ScopeTransition;
import java.util.Comparator;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(exclude = {"codegen", "scopeTransition", "strategyClassFqn"})
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
    /** {@code PRESERVING}/{@code ENTERING}/{@code EXITING}, persisted from the producing {@code BridgeStep}. */
    ScopeTransition scopeTransition;

    Optional<String> strategyClassFqn;

    Edge(
            final Node from,
            final Node to,
            final int weight,
            final EdgeKind kind,
            final Optional<AnnotationMirror> directive,
            final Optional<Codegen> codegen,
            final ScopeTransition scopeTransition,
            final Optional<String> strategyClassFqn) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.kind = kind;
        this.directive = directive;
        this.codegen = codegen;
        this.scopeTransition = scopeTransition;
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
                ScopeTransition.PRESERVING,
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
                original.scopeTransition,
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
                ScopeTransition.PRESERVING,
                Optional.of(strategyClassFqn));
    }

    public static Edge realised(
            final Node from,
            final Node to,
            final int weight,
            final Codegen provider,
            final ScopeTransition scopeTransition,
            final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                Optional.of(provider),
                scopeTransition,
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
                ScopeTransition.PRESERVING,
                Optional.of(strategyClassFqn));
    }

    @Override
    public int compareTo(final Edge other) {
        return EDGE_ORDER.compare(this, other);
    }
}
