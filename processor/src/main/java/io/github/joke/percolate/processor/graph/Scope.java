package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Nullability;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

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
     * This scope's base-case input declarations (graph-expansion "Scopes declare base-case inputs uniformly"): a
     * method scope yields one per parameter, a child (element) scope its single element input, the mapper root none
     * (the default). {@code nullness} resolves a source element's declared nullness — used by a method scope for its
     * parameters; a scope whose input nullness is already known ignores it. Declarations are lazy: the driver
     * materialises one into a {@code LEAF} source {@link Value} only when a port reuses it, so an unreferenced input
     * never enters the graph.
     */
    default Stream<InputDecl> inputDecls(final BiFunction<TypeMirror, Element, Nullability> nullness) {
        return Stream.empty();
    }
}
