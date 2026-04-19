package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueNode;
import java.util.Set;
import lombok.Value;

/**
 * Fragment contributed by a {@link ValueExpansionStrategy} in response to an {@link
 * ExpansionDemand}. {@code BuildValueGraphStage} merges the fragment into the mapper-level graph,
 * wiring {@link #getExit()} into the demand's requester when the merge rule for the {@link
 * DemandKind} requires it.
 *
 * <p>{@link #getVertices()} MAY reference nodes already present in the parent graph — JGraphT's
 * vertex identity ({@code .equals()}) is used to deduplicate.
 */
@Value
public class Subgraph {
    Set<ValueNode> vertices;
    Set<ValueEdge> edges;
    ValueNode entry;
    ValueNode exit;
}
