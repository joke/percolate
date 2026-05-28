package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import lombok.Value;

/**
 * Adds a REALISED {@link Edge} to the underlying graph. Subject to the bundle-level cycle check in
 * {@link Applier}: if the edge would close a cycle in the REALISED projection, the whole enclosing
 * {@link DeltaBundle} is rejected.
 */
@Value
public class AddEdge implements Delta {
    Edge edge;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddEdge(this);
    }
}
