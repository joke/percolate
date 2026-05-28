package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import lombok.Value;

/**
 * Adds an already-present graph {@link Edge} to an existing {@link ExpansionGroup}'s view. Used by the
 * path-segment and directive-binding expanders to record the realised edge they just emitted into the group
 * being expanded.
 */
@Value
public class AddEdgeToView implements Delta {
    ExpansionGroup group;
    Edge edge;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddEdgeToView(this);
    }
}
