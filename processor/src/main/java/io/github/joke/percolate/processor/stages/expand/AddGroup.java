package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.spi.GroupCodegen;
import io.github.joke.percolate.spi.Slot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Registers a fresh nested {@link io.github.joke.percolate.processor.graph.ExpansionGroup}. The delta carries
 * the construction ingredients rather than a pre-built group, because {@code ExpansionGroup.of} validates
 * membership against the live graph — which a pure expander cannot touch. The {@link Applier} builds the
 * group once the preceding {@link AddNode}/{@link AddEdge} deltas in the same bundle have been applied, then
 * imports the {@link #boundaryImports} nodes into its view (the boundary-import step the older code performed
 * separately).
 */
@Value
public class AddGroup implements Delta {
    Node root;
    List<Node> slots;
    GroupCodegen codegen;
    String strategyClassFqn;
    Set<Edge> initialEdges;
    Map<Node, Slot> slotMetadata;
    List<Node> boundaryImports;

    @Override
    public <R> R accept(final Delta.Visitor<R> visitor) {
        return visitor.visitAddGroup(this);
    }
}
