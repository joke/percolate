package io.github.joke.percolate.processor.internal.graph;

/**
 * Whether a scope's input declaration is reachable only within its own scope, or also by name from a descendant
 * scope (design D5 of change {@code decouple-engine-from-strategy-semantics}). Orthogonal to how a port selects
 * ({@code BY_TYPE}/{@code BY_NAME}, see {@link io.github.joke.percolate.spi.Port}): visibility never widens
 * {@code BY_TYPE} matching, which always stays scope-own-only.
 */
public enum Visibility {

    /** Reachable only within the declaring scope, by type or by name. */
    LOCAL,

    /** Additionally reachable by name from a descendant scope, walking to the nearest ancestor declaring it. */
    INHERITED
}
