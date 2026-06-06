package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ElementScope;
import io.github.joke.percolate.spi.Slot;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Edge <em>payload</em> only — it carries no endpoints. The {@code (source, target)} topology of every edge is
 * maintained solely by the underlying JGraphT {@code DirectedMultigraph<Node, Edge>}; consumers obtain an edge's
 * endpoints from the graph (or a JGraphT view) via {@code getEdgeSource}/{@code getEdgeTarget}, never from the
 * {@code Edge} value. Endpoints are supplied at mutation time to {@link MapperGraph#addEdge(Node, Node, Edge)}.
 *
 * <p>Equality is <strong>instance identity</strong> (like {@link Node}): the former structural value-equality
 * existed only to drive the graph's now-removed dedup index. Duplicate-edge prevention is owned by the mutation
 * sites ({@code SeedStage}, the expansion {@code Applier}), not by {@code Edge} equality. Deterministic edge
 * ordering is computed by the graph from endpoint ids, not by an {@code Edge}-internal {@code Comparable}.
 */
@Getter
@ToString
public final class Edge {

    private final int weight;
    private final EdgeKind kind;
    private final Optional<AnnotationMirror> directive;
    private final Optional<Codegen> codegen;

    /**
     * The element-scope crossing of a container edge ({@code ENTERING} / {@code EXITING}), or empty for a scalar
     * (scope-preserving) edge. Persisted from the producing {@link io.github.joke.percolate.spi.ExpansionStep}'s
     * {@link ElementScope}; consumed by code-generation to weave the container operation.
     */
    private final Optional<ElementScope> elementScope;

    private final Optional<String> strategyClassFqn;

    /**
     * The consumer {@link Slot} this edge wires (the declared input type and the {@code AnnotatedConstruct
     * producedFrom} consumer contract), for a {@code REALISED} operand edge feeding an n-ary producer. Empty for
     * scalar/container/seed edges. Code generation reads the consumer contract from this slot — never from an
     * {@code ExpansionGroup}.
     */
    private final Optional<Slot> consumerSlot;

    private Edge(
            final int weight,
            final EdgeKind kind,
            final Optional<AnnotationMirror> directive,
            final Optional<Codegen> codegen,
            final Optional<ElementScope> elementScope,
            final Optional<String> strategyClassFqn,
            final Optional<Slot> consumerSlot) {
        this.weight = weight;
        this.kind = kind;
        this.directive = directive;
        this.codegen = codegen;
        this.elementScope = elementScope;
        this.strategyClassFqn = strategyClassFqn;
        this.consumerSlot = consumerSlot;
    }

    public static Edge seed(final Optional<AnnotationMirror> directive) {
        return new Edge(
                Weights.SENTINEL_UNREALISED,
                EdgeKind.SEED,
                directive,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static Edge seedForTest() {
        return seed(Optional.empty());
    }

    public static Edge realised(final int weight, final EdgeCodegen codegen, final String strategyClassFqn) {
        return realised(weight, codegen, strategyClassFqn, null);
    }

    public static Edge realised(
            final int weight,
            final EdgeCodegen codegen,
            final String strategyClassFqn,
            final @Nullable Slot consumerSlot) {
        return new Edge(
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                Optional.of(codegen),
                Optional.empty(),
                Optional.of(strategyClassFqn),
                Optional.ofNullable(consumerSlot));
    }

    public static Edge realised(
            final int weight, final Codegen provider, final ElementScope elementScope, final String strategyClassFqn) {
        return realised(weight, provider, elementScope, strategyClassFqn, null);
    }

    public static Edge realised(
            final int weight,
            final Codegen provider,
            final ElementScope elementScope,
            final String strategyClassFqn,
            final @Nullable Slot consumerSlot) {
        return new Edge(
                weight,
                EdgeKind.REALISED,
                Optional.empty(),
                Optional.of(provider),
                Optional.of(elementScope),
                Optional.of(strategyClassFqn),
                Optional.ofNullable(consumerSlot));
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
