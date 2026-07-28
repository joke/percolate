package io.github.joke.percolate.processor.internal.graph;

// Whether a scope's input declaration is reachable only within its own scope, or also by name from a descendant
// scope (design D5 of change decouple-engine-from-strategy-semantics). Orthogonal to how a port selects
// (BY_TYPE/BY_NAME, see io.github.joke.percolate.spi.Port): visibility never widens BY_TYPE matching, which
// always stays scope-own-only.
public enum Visibility {

    // Reachable only within the declaring scope, by type or by name.
    LOCAL,

    // Additionally reachable by name from a descendant scope, walking to the nearest ancestor declaring it.
    INHERITED
}
