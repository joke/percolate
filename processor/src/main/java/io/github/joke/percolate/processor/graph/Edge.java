package io.github.joke.percolate.processor.graph;

import java.util.Comparator;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(exclude = {"codegen", "groupId"})
public final class Edge implements Comparable<Edge> {

    private static final Comparator<Edge> EDGE_ORDER = Comparator.<Edge, String>comparing(e -> e.from.id())
            .thenComparing(e -> e.to.id())
            .thenComparingInt(e -> e.weight)
            .thenComparing(e -> e.kind)
            .thenComparing(e -> e.directive.isPresent())
            .thenComparing(e -> e.strategyClassFqn.orElse(null), Comparator.nullsFirst(Comparator.naturalOrder()));
    Node from;
    Node to;
    int weight;
    EdgeKind kind;
    Optional<AnnotationMirror> directive;
    Optional<String> groupId;
    Optional<EdgeCodegen> codegen;
    Optional<String> strategyClassFqn;

    Edge(
            final Node from,
            final Node to,
            final int weight,
            final EdgeKind kind,
            final Optional<AnnotationMirror> directive,
            final Optional<String> groupId,
            final Optional<EdgeCodegen> codegen,
            final Optional<String> strategyClassFqn) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.kind = kind;
        this.directive = directive;
        this.groupId = groupId;
        this.codegen = codegen;
        this.strategyClassFqn = strategyClassFqn;
    }

    public static Edge seed(final Node from, final Node to, final AnnotationMirror directive) {
        return new Edge(
                from,
                to,
                Weights.SENTINEL_UNREALISED,
                EdgeKind.SEED,
                Optional.of(directive),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public static Edge elementSeed(final Node from, final Node to, final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                Weights.SENTINEL_UNREALISED,
                EdgeKind.SEED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(strategyClassFqn));
    }

    public static Edge realised(
            final Node from,
            final Node to,
            final int weight,
            final Optional<String> groupId,
            final EdgeCodegen codegen,
            final String strategyClassFqn) {
        return new Edge(
                from,
                to,
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                groupId,
                Optional.of(codegen),
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

    public static Edge subSeed(
            final Node from, final Node to, final String strategyClassFqn, final Optional<AnnotationMirror> directive) {
        return new Edge(
                from,
                to,
                Weights.SENTINEL_UNREALISED,
                EdgeKind.SUB_SEED,
                directive,
                Optional.empty(),
                Optional.empty(),
                Optional.of(strategyClassFqn));
    }

    @Override
    public int compareTo(final Edge other) {
        return EDGE_ORDER.compare(this, other);
    }
}
