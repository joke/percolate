package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import lombok.Value;

/** Adds a {@link Node} as a vertex of the underlying graph. */
@Value
public class AddNode implements Delta {
    Node node;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddNode(this);
    }
}
