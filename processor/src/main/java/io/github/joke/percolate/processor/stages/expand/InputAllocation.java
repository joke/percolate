package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of {@link InputAllocator} deciding a bridge step's input node. {@link #node} is the node to wire
 * into the REALISED edge; {@link #addNode} is the {@link AddNode} delta to emit when the node was freshly
 * allocated, or {@code null} when an existing candidate was reused.
 */
@Value
public class InputAllocation {
    Node node;

    @Nullable
    AddNode addNode;
}
