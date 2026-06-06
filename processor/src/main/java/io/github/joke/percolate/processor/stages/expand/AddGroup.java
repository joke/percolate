package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import java.util.List;
import lombok.Value;

/**
 * Registers a fresh {@link io.github.joke.percolate.processor.graph.ExpansionGroup} as a label. The
 * {@link Applier} mints a {@link io.github.joke.percolate.processor.graph.GroupId} (marked {@link #seed}),
 * creates the label-only group rooted at {@link #root}, and tags the {@link #inputs} (the demand slots) and the
 * {@link #boundaryImports} (source context visible to the group's candidate search) onto their {@link Node}s.
 * The slot {@code REALISED}/{@code SEED} edges are added by the preceding {@link AddEdge}/seed deltas; the group
 * carries no slots, codegen, or view of its own (the view is derived from the node tags).
 */
@Value
public class AddGroup implements Delta {
    Node root;
    List<Node> inputs;
    List<Node> boundaryImports;
    boolean seed;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddGroup(this);
    }
}
