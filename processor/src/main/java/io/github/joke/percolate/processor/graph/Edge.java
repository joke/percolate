package io.github.joke.percolate.processor.graph;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.lang.model.element.AnnotationMirror;
import java.util.Optional;

@Value
@EqualsAndHashCode(exclude = {"codegen", "groupId"})
public final class Edge implements Comparable<Edge> {
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
        var c = this.from.id().compareTo(other.from.id());
        if (c != 0) {
            return c;
        }
        c = this.to.id().compareTo(other.to.id());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.weight, other.weight);
        if (c != 0) {
            return c;
        }
        c = this.kind.compareTo(other.kind);
        if (c != 0) {
            return c;
        }
        c = Boolean.compare(this.directive.isPresent(), other.directive.isPresent());
        if (c != 0) {
            return c;
        }
        if (!this.strategyClassFqn.isPresent() && !other.strategyClassFqn.isPresent()) {
            return 0;
        }
        if (!this.strategyClassFqn.isPresent()) {
            return -1;
        }
        if (!other.strategyClassFqn.isPresent()) {
            return 1;
        }
        return this.strategyClassFqn.get().compareTo(other.strategyClassFqn.get());
    }
}
