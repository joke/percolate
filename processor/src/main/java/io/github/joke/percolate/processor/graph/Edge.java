package io.github.joke.percolate.processor.graph;

import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(exclude = {"codegen", "strategyClassFqn"})
public final class Edge implements Comparable<Edge> {
    Node from;
    Node to;
    int weight;
    EdgeKind kind;
    Optional<AnnotationMirror> directive;
    Optional<String> groupId;
    Optional<EdgeCodegen> codegen;
    Optional<String> strategyClassFqn;

    Edge(Node from, Node to, int weight, EdgeKind kind,
         Optional<AnnotationMirror> directive, Optional<String> groupId,
         Optional<EdgeCodegen> codegen, Optional<String> strategyClassFqn) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.kind = kind;
        this.directive = directive;
        this.groupId = groupId;
        this.codegen = codegen;
        this.strategyClassFqn = strategyClassFqn;
    }

    public static Edge seed(Node from, Node to, AnnotationMirror directive) {
        return new Edge(from, to, Weights.SENTINEL_UNREALISED, EdgeKind.SEED,
                Optional.of(directive), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    public static Edge realised(Node from, Node to, int weight,
                                Optional<String> groupId,
                                EdgeCodegen codegen,
                                String strategyClassFqn) {
        return new Edge(from, to, weight, EdgeKind.REALISED,
                Optional.empty(), groupId,
                Optional.of(codegen), Optional.of(strategyClassFqn));
    }

    public static Edge marker(Node from, Node to, String strategyClassFqn) {
        return new Edge(from, to, Weights.NOOP, EdgeKind.MARKER,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(strategyClassFqn));
    }

    public static Edge subSeed(Node from, Node to, String strategyClassFqn) {
        return new Edge(from, to, Weights.SENTINEL_UNREALISED, EdgeKind.SUB_SEED,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(strategyClassFqn));
    }

    @Override
    public int compareTo(final Edge other) {
        int c = this.from.id().compareTo(other.from.id());
        if (c != 0) return c;
        c = this.to.id().compareTo(other.to.id());
        if (c != 0) return c;
        c = Integer.compare(this.weight, other.weight);
        if (c != 0) return c;
        c = this.kind.compareTo(other.kind);
        if (c != 0) return c;
        c = Boolean.compare(this.directive.isPresent(), other.directive.isPresent());
        if (c != 0) return c;
        if (!this.groupId.isPresent() && !other.groupId.isPresent()) return 0;
        if (!this.groupId.isPresent()) return -1;
        if (!other.groupId.isPresent()) return 1;
        return this.groupId.get().compareTo(other.groupId.get());
    }
}
