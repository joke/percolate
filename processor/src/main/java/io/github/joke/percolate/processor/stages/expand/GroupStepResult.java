package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import java.util.List;
import lombok.Value;

/**
 * The result of one {@link GroupExpander#step} call: the {@link #bundles} of intended mutations, and the
 * group's {@link #pendingSlots} — the slots not yet satisfied after applying those bundles. An empty
 * {@code pendingSlots} list signals that the expander considers the group SAT; the driver records SAT, no
 * explicit delta required.
 */
@Value
public class GroupStepResult {
    List<DeltaBundle> bundles;
    List<Node> pendingSlots;
}
