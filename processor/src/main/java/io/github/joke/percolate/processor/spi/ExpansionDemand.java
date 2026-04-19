package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.graph.ValueNode;
import lombok.Value;

/**
 * A single demand issued by {@code BuildValueGraphStage}'s worklist to each registered
 * {@link ValueExpansionStrategy}.
 *
 * <p>{@link #getRequester()} is the node that still needs an incoming edge (the downstream /
 * target side). {@link #getDemand()} is the node or type representation the strategy is asked to
 * produce — for {@link DemandKind#TYPE_TRANSFORM} demands this is the typed node whose incoming
 * edge must be contributed. {@link #getKind()} narrows the demand for strategy dispatch.
 */
@Value
public class ExpansionDemand {
    ValueNode requester;
    ValueNode demand;
    DemandKind kind;
}
