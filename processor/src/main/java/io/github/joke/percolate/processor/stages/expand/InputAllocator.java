package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.spi.ElementScope;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Comparator;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Decides the input node for a {@code BOUNDARY} step's slot, applying the element-scope placement rule: no scope
 * (a plain boundary) places the input as a same-scope sibling of the frontier; {@link ElementScope#ENTERING}
 * reuses a non-element candidate or sinks the input under the frontier; {@link ElementScope#EXITING} places the
 * input at an {@link ElementLocation} child. Pure: it reads only the group's view (via the snapshot) and either
 * reuses an existing candidate or describes a fresh node with an {@link AddNode} delta. Fresh allocation always
 * produces a distinct {@link Node} instance (instance identity), even when an equal node already exists.
 *
 * <p>{@code CONVERSION} inputs do <em>not</em> go through here: a conversion binds to an existing in-view
 * candidate (it folds an edge from a value that already exists), it never synthesizes a fresh input node.
 */
@RequiredArgsConstructor
final class InputAllocator {

    private final ResolveCtx resolveCtx;

    InputAllocation allocate(
            final TypeMirror inputType,
            final Optional<ElementScope> scope,
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        if (scope.isEmpty()) {
            return allocateForPreserving(inputType, frontier, group, snapshot);
        }
        switch (scope.get()) {
            case ENTERING:
                return allocateForEntering(inputType, frontier, group, snapshot);
            case EXITING:
                return allocateForExiting(inputType, frontier);
        }
        throw new IllegalStateException("Unknown element scope: " + scope.get());
    }

    private InputAllocation allocateForPreserving(
            final TypeMirror inputType,
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        final var typeMatch = findCandidateByInputType(inputType, frontier, group, snapshot);
        if (typeMatch != null && typeMatch.getLoc().equals(frontier.getLoc())) {
            return new InputAllocation(typeMatch, null);
        }
        return allocateFresh(inputType, frontier.getLoc(), frontier, frontier.getParent());
    }

    private InputAllocation allocateForEntering(
            final TypeMirror inputType,
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        final var typeMatch = findCandidateByInputType(inputType, frontier, group, snapshot);
        if (typeMatch != null && !(typeMatch.getLoc() instanceof ElementLocation)) {
            return new InputAllocation(typeMatch, null);
        }
        final Optional<Node> parent =
                frontier.getLoc() instanceof ElementLocation ? Optional.of(frontier) : Optional.empty();
        return allocateFresh(inputType, frontier.getLoc(), frontier, parent);
    }

    private InputAllocation allocateForExiting(final TypeMirror inputType, final Node frontier) {
        return allocateFresh(inputType, new ElementLocation(), frontier, Optional.of(frontier));
    }

    private InputAllocation allocateFresh(
            final TypeMirror inputType, final Location loc, final Node frontier, final Optional<Node> parent) {
        final var fresh = new Node(Optional.of(inputType), loc, frontier.getScope(), parent);
        return new InputAllocation(fresh, new AddNode(fresh));
    }

    @Nullable
    private Node findCandidateByInputType(
            final TypeMirror inputType,
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> !(node.getLoc() instanceof TargetLocation))
                .filter(node -> node.getType().isPresent())
                .filter(node -> resolveCtx.types().isSameType(node.getType().get(), inputType))
                .min(Comparator.comparing(Node::id))
                .orElse(null);
    }
}
