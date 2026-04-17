package io.github.joke.percolate.processor.graph;

/**
 * Edge from a {@link SourceParamNode} or {@link PropertyNode} to a {@link PropertyNode},
 * representing a getter or field read.
 *
 * <p>Carries no code template of its own — the read expression derives from the target
 * {@link PropertyNode#getReadAccessor()}.
 */
public final class PropertyReadEdge extends ValueEdge {

    public PropertyReadEdge() {}

    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitPropertyRead(this);
    }

    @Override
    public String toString() {
        return "PropertyReadEdge";
    }
}
