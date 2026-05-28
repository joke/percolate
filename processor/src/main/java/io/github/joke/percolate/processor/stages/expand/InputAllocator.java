package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Decides the input node for a matched {@link BridgeStep}, applying the PRESERVING / ENTERING / EXITING rule.
 * Pure: it reads only the group's view (via the snapshot) and either reuses an existing candidate or describes
 * a fresh node with an {@link AddNode} delta. Fresh allocation always produces a distinct {@link Node}
 * instance (instance identity), even when an equal node already exists elsewhere in the graph.
 */
@RequiredArgsConstructor
final class InputAllocator {

    private final ResolveCtx resolveCtx;

    InputAllocation allocate(
            final BridgeStep step, final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return allocateForPreserving(step, frontier, group, snapshot);
            case ENTERING:
                return allocateForEntering(step, frontier, group, snapshot);
            case EXITING:
                return allocateForExiting(step, frontier);
        }
        throw new IllegalStateException("Unknown scope transition: " + step.getScopeTransition());
    }

    private InputAllocation allocateForPreserving(
            final BridgeStep step, final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var typeMatch = findCandidateByInputType(step, frontier, group, snapshot);
        if (typeMatch != null && typeMatch.getLoc().equals(frontier.getLoc())) {
            return new InputAllocation(typeMatch, null);
        }
        return allocateFresh(step, frontier.getLoc(), frontier);
    }

    private InputAllocation allocateForEntering(
            final BridgeStep step, final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var typeMatch = findCandidateByInputType(step, frontier, group, snapshot);
        if (typeMatch != null && !(typeMatch.getLoc() instanceof ElementLocation)) {
            return new InputAllocation(typeMatch, null);
        }
        return allocateFresh(step, frontier.getLoc(), frontier);
    }

    private InputAllocation allocateForExiting(final BridgeStep step, final Node frontier) {
        return allocateFresh(step, new ElementLocation(step.getElementRole()), frontier);
    }

    private InputAllocation allocateFresh(final BridgeStep step, final Location loc, final Node frontier) {
        final Optional<Node> parent = loc instanceof ElementLocation ? Optional.of(frontier) : Optional.empty();
        final var fresh = new Node(Optional.of(step.getInputType()), loc, frontier.getScope(), parent);
        return new InputAllocation(fresh, new AddNode(fresh));
    }

    @Nullable
    private Node findCandidateByInputType(
            final BridgeStep step, final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> !(node.getLoc() instanceof TargetLocation))
                .filter(node -> node.getType().isPresent())
                .filter(node -> resolveCtx.types().isSameType(node.getType().get(), step.getInputType()))
                .min(Comparator.comparing(Node::id))
                .orElse(null);
    }
}
