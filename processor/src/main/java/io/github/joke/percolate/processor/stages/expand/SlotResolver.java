package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a single frontier (a group slot, or a directive-binding root) to SAT or pending, appending any
 * expansion bundles it produces. Shared by the kind-specific expanders.
 *
 * <p>A frontier is SAT when it is a parameter-root base case, when a realised edge already feeds it inside the
 * group's view (a folded CONVERSION edge), or when a child sub-group rooted at it is SAT. Otherwise, if it has an
 * effective type and no child group yet, it is expanded through the single {@link FrontierMatcher} strategy round.
 * Expansion never SATs the frontier in the same pass; SAT arrives once a folded edge or a spawned child lands.
 */
@RequiredArgsConstructor
final class SlotResolver {

    private final FrontierMatcher frontierMatcher;

    boolean resolve(
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final List<DeltaBundle> out) {
        if (isParameterRootSlot(frontier)) {
            return true;
        }
        if (producedInView(frontier, group, snapshot)) {
            return true;
        }
        if (hasSatChildAt(frontier, snapshot)) {
            return true;
        }
        final var effectiveType = snapshot.effectiveTypeFor(frontier, group);
        if (effectiveType == null) {
            return false;
        }
        if (hasAnyChildAt(frontier, group, snapshot)) {
            return false;
        }
        out.addAll(frontierMatcher.matchAt(frontier, group, snapshot));
        return false;
    }

    boolean isParameterRootSlot(final Node frontier) {
        // Real param roots resolve against the node's own MethodScope. With no MethodScope (a synthetic scope, e.g.
        // in isolated unit graphs) a typed single-segment source has no further source chain, so it is terminal —
        // matching the prior behaviour when no current method was set.
        return SourceParams.forSlot(frontier) != null
                || (!(frontier.getScope() instanceof MethodScope) && isTypedSingleSegmentSource(frontier));
    }

    private static boolean isTypedSingleSegmentSource(final Node frontier) {
        return frontier.getLoc() instanceof SourceLocation
                && frontier.getType().isPresent()
                && ((SourceLocation) frontier.getLoc()).getPath().getSegments().size() == 1;
    }

    /** A realised edge already feeds {@code node} inside the group's view (a folded conversion or descent edge). */
    boolean producedInView(final Node node, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return !snapshot.viewOf(group).incomingEdgesOf(node).isEmpty();
    }

    boolean hasSatChildAt(final Node node, final ExpansionSnapshot snapshot) {
        return snapshot.groups().anyMatch(g -> snapshot.isSat(g) && g.getRoot().equals(node));
    }

    boolean hasAnyChildAt(final Node node, final ExpansionGroup excluding, final ExpansionSnapshot snapshot) {
        return snapshot.groups()
                .anyMatch(g -> !g.equals(excluding) && g.getRoot().equals(node));
    }
}
