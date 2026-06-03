package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import lombok.Value;

/**
 * Registers a synthesized {@code CONVERSION} input node as an expandable frontier of {@code group}: the
 * {@link Applier} adds it to the group's view (so {@code AddEdgeToView} can attach the fold edge and candidate
 * search sees it) and to the group's conversion-frontier set (so the group expander offers it to strategies in
 * later passes without making it an AND-required slot). An unreachable conversion frontier is a retained dead
 * end, not a blocker (design E2).
 */
@Value
public class RegisterConversionFrontier implements Delta {
    ExpansionGroup group;
    Node node;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitRegisterConversionFrontier(this);
    }
}
