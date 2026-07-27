package io.github.joke.percolate.processor.internal.graph;

import java.util.Optional;
import java.util.stream.Stream;

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

    /**
     * This scope's base-case input declarations (graph-expansion "Scopes declare base-case inputs uniformly",
     * design D5 of change {@code decouple-engine-from-strategy-semantics}): a method scope yields one per
     * parameter, a child (element) scope its single element input, the mapper root none (the default). Each
     * declaration already carries its resolved nullness, a name, and a {@link Visibility} — {@code Scope} takes
     * no nullness-resolving callback and reads no annotation. Declarations are lazy: the driver materialises one
     * into a {@code LEAF} source {@link Value} only when a port reuses it, so an unreferenced input never enters
     * the graph. A {@code BY_TYPE} port only ever matches a scope's own declarations; a {@code BY_NAME} port
     * additionally walks to the nearest ancestor declaring the name {@link Visibility#INHERITED}.
     */
    default Stream<InputDecl> inputDecls() {
        return Stream.empty();
    }
}
