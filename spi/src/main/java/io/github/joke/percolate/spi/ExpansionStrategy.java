package io.github.joke.percolate.spi;

import java.util.stream.Stream;

/**
 * The sole strategy-author interface for expansion. A strategy answers one of two questions and overrides
 * <b>exactly one</b> method: {@link #expand} (a producer: "what produces this demanded target?") or {@link #descend}
 * (an accessor: "what does reading this segment off this parent yield?"). The driver dispatches a {@link ProduceDemand}
 * to {@code expand} and a {@link DescendDemand} to {@code descend}, and is the <b>sole invoker</b> of both — no helper
 * invokes a strategy. An implementation makes a purely local decision from its {@link Demand} and returns the
 * {@link Offer}s it can make — a production carrying an {@link OperationSpec}, or a refusal carrying a {@link Subject}
 * and a message when the demand is recognisably its own but cannot be served — or {@link Stream#empty()} when it does
 * not apply at all. Implementations MUST NOT throw on a non-applicable demand, MUST NOT throw to report a refusal, and
 * MUST NOT receive or traverse the graph, nor read a candidate snapshot.
 */
public interface ExpansionStrategy {

    /** A producer answers what produces the demanded target; default empty so accessors need not implement it. */
    default Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        return Stream.empty();
    }

    /** An accessor answers what reading one segment off a parent yields; default empty so producers need not. */
    default Stream<Offer> descend(final DescendDemand demand, final ResolveCtx ctx) {
        return Stream.empty();
    }

    default int priority() {
        return 0;
    }
}
