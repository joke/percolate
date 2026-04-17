package io.github.joke.percolate.processor.graph;

/**
 * No-op edge that widens the nullness of the value flowing through it.
 *
 * <p>Declared for future use by the {@code jspecify-nullability} change. In the
 * {@code value-graph-refactor}, no {@code NullWidenEdge} is ever constructed — it is a
 * dormant subtype only. {@code GenerateStage} SHALL throw {@link IllegalStateException} if it
 * encounters one.
 */
public final class NullWidenEdge extends ValueEdge {

    public NullWidenEdge() {}

    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitNullWiden(this);
    }

    @Override
    public String toString() {
        return "NullWidenEdge";
    }
}
