package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.Node;
import lombok.Value;

/**
 * Adds an {@link Edge} between {@code from} and {@code to} in the underlying graph. Endpoints travel with the
 * delta because they no longer live on the {@code Edge} value. Subject to the bundle-level cycle check in
 * {@link Applier}: if the edge would close a cycle in the REALISED projection, the whole enclosing
 * {@link DeltaBundle} is rejected.
 */
@Value
public class AddEdge implements Delta {
    Node from;
    Node to;
    Edge edge;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddEdge(this);
    }
}
