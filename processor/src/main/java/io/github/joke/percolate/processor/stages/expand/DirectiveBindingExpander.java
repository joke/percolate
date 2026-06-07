package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Binds a target leaf (root) to a source leaf (slot). The root's type is the declared target type pinned onto the
 * group; the source slot's type is never stamped onto the root. The slot is resolved first (descended through its
 * own source chain); once it is resolved the root is produced from the in-view candidates via the single strategy
 * round ({@link FrontierMatcher#matchAt}): a same-type match folds a CONVERSION (direct-assign) edge into this
 * group, a type mismatch opens a BOUNDARY conversion sub-group. The group is SAT when both the slot is resolved
 * and the root has a producing edge or a SAT child.
 */
@RequiredArgsConstructor
final class DirectiveBindingExpander implements GroupExpander {

    private final SlotResolver slotResolver;
    private final FrontierMatcher frontierMatcher;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isDirectiveBinding(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var slot = group.inputs().get(0);
        final var root = group.getRoot();
        final var bundles = new ArrayList<DeltaBundle>();
        final var slotResolved = slotResolver.resolve(slot, group, snapshot, bundles);
        final var rootReachable = slotResolver.reachable(root, group, snapshot);
        final var rootExpanded =
                !snapshot.viewOf(group).incomingEdgesOf(root).isEmpty() || slotResolver.hasSatChildAt(root, snapshot);
        if (slotResolved
                && !rootExpanded
                && snapshot.effectiveTypeFor(root) != null
                && !slotResolver.hasAnyChildAt(root, group, snapshot)) {
            bundles.addAll(frontierMatcher.matchAt(root, group, snapshot));
        }
        slotResolver.expandConversionFrontiers(group, snapshot, bundles);
        if (slotResolved && rootReachable) {
            return new GroupStepResult(bundles, List.of());
        }
        return new GroupStepResult(bundles, List.of(slotResolved ? root : slot));
    }
}
