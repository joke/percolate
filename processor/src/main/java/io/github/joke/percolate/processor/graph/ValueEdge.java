package io.github.joke.percolate.processor.graph;

/**
 * Typed edge in a per-method {@link org.jgrapht.graph.DefaultDirectedGraph ValueGraph}.
 *
 * <p>Subtypes are restricted to this package via a package-private constructor, approximating
 * Java 17 {@code sealed} semantics for Java 11. The four permitted subtypes are:
 * {@link PropertyReadEdge}, {@link TypeTransformEdge}, {@link NullWidenEdge}, {@link LiftEdge}.
 */
public abstract class ValueEdge {

    /** Package-private — prevents subclassing from outside this package. */
    ValueEdge() {}

    /**
     * Compile-time exhaustive dispatch over the four permitted subtypes via {@link
     * ValueEdgeVisitor}. Eliminates the need for {@code instanceof} ladders or default branches in
     * callers.
     */
    public abstract <R> R accept(ValueEdgeVisitor<R> visitor);
}
