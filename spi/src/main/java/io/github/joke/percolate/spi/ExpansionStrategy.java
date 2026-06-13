package io.github.joke.percolate.spi;

import java.util.stream.Stream;

/**
 * The sole strategy-author interface for expansion. Every strategy — conversion, getter/field access, method call,
 * assembly, or container — implements this one interface and is loaded into one {@code ServiceLoader} list tried as
 * a single round each pass (no per-kind ordering). An implementation makes a purely local decision from the
 * {@link Demand} and returns the {@link OperationSpec}s it can offer, or {@link Stream#empty()} when it does not
 * apply. Implementations MUST NOT throw on a non-applicable demand, and MUST NOT receive or traverse the graph.
 */
public interface ExpansionStrategy {

    Stream<OperationSpec> expand(Demand demand, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
