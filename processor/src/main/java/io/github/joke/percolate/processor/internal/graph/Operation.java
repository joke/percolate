package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.MemberRequest;
import io.github.joke.percolate.spi.Port;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/**
 * A single production (constructor call, accessor, conversion, container operation, constant): the AND-kind
 * vertex of the bipartite graph — it is usable only when every port of its ordered {@link Port} signature is
 * fed. The operation owns the consumer contract (the former edge-carried {@code Slot}), its codegen, its
 * weight, and a {@code partial} flag (true when the production may throw on a structurally-valid input — e.g.
 * {@code Optional.orElseThrow}, {@code requireNonNull} — which the plan-extraction totality rule deprioritises).
 * Its {@code label} is the strategy-supplied, fully-typed production description (e.g. {@code int→long}) — never
 * the codegen handle's runtime class. A container element mapping additionally owns a {@link ChildScope} whose
 * param/return roots are the only coupling between the child plan and this operation.
 *
 * <p>Equality is instance identity; the graph-assigned {@code seq} keeps {@link #id()} deterministic for
 * ordering and rendering.
 */
@Getter
public final class Operation implements GraphVertex {

    private final int seq;
    private final String label;
    private final Codegen codegen;
    private final int weight;
    private final boolean partial;
    private final List<Port> ports;
    private final Scope scope;
    private final Optional<ChildScope> childScope;
    private final Set<String> consumedOptionKeys;
    private final List<MemberRequest> memberRequests;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    Operation(
            final int seq,
            final String label,
            final Codegen codegen,
            final int weight,
            final boolean partial,
            final List<Port> ports,
            final Scope scope,
            final boolean ownsChildScope,
            final Set<String> consumedOptionKeys,
            final List<MemberRequest> memberRequests) {
        this.seq = seq;
        this.label = label;
        this.codegen = codegen;
        this.weight = weight;
        this.partial = partial;
        this.ports = List.copyOf(ports);
        this.scope = scope;
        this.childScope = ownsChildScope ? Optional.of(new ChildScope(this, scope)) : Optional.empty();
        this.consumedOptionKeys = Set.copyOf(consumedOptionKeys);
        this.memberRequests = List.copyOf(memberRequests);
    }

    @Override
    public String id() {
        return scope.encode() + "::op" + seq + "::" + label;
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
