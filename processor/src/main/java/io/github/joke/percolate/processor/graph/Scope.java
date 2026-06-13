package io.github.joke.percolate.processor.graph;

import java.util.Optional;

/**
 * A node of the scope tree: the mapper scope at the root, method scopes beneath it, and {@link ChildScope}s
 * owned by scope-owning {@link Operation}s (container element mappings) beneath those. No {@link Dep} edge
 * ever crosses a scope boundary; the only parent↔child coupling is the owning Operation.
 */
public interface Scope {
    String encode();

    /** The parent scope, or empty at the tree root. */
    default Optional<Scope> parent() {
        return Optional.empty();
    }
}
