package io.github.joke.percolate.processor.graph;

/**
 * Compile-time exhaustive dispatch over the {@link ValueEdge} subtypes.
 *
 * <p>Implementations MUST handle every concrete subtype permitted by {@link ValueEdge}. Adding a
 * fifth {@code ValueEdge} subtype requires updating this interface (a method per subtype) and
 * every visitor implementation, eliminating the need for an {@code instanceof} ladder or a
 * default branch.
 *
 * @param <R> the result type produced by visiting an edge
 */
public interface ValueEdgeVisitor<R> {

    R visitPropertyRead(PropertyReadEdge edge);

    R visitTypeTransform(TypeTransformEdge edge);

    R visitNullWiden(NullWidenEdge edge);

    R visitLift(LiftEdge edge);
}
