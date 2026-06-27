package io.github.joke.percolate.processor.internal.graph;

import java.util.Optional;
import lombok.Getter;
import lombok.ToString;

/**
 * A pure dependency edge of the bipartite graph — payload only, carrying no endpoints (topology is maintained
 * solely by the graph and supplied at mutation time). A {@code Dep} into an {@link Operation} carries the
 * {@link #portId} it feeds; a {@code Dep} into a {@link Value} (from its producer Operation) carries none.
 * It carries no codegen, weight, kind, element scope, or consumer slot — function payload lives on the
 * {@link Operation} vertex.
 *
 * <p>Equality is instance identity: parallel {@code Dep}s from one Value into two ports of one Operation are
 * distinct edge instances distinguished by their port id.
 */
@Getter
@ToString
public final class Dep {

    private final Optional<String> portId;

    private Dep(final Optional<String> portId) {
        this.portId = portId;
    }

    /** A dependency feeding the named port of an {@link Operation}. */
    public static Dep port(final String portId) {
        return new Dep(Optional.of(portId));
    }

    /** The output dependency from a producer {@link Operation} into the {@link Value} it produces. */
    public static Dep output() {
        return new Dep(Optional.empty());
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
