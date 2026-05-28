package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;

/**
 * The fallback expander for any group that is neither a path-segment nor a directive-binding group: the
 * GroupTarget-built and bridge-spawned sub-groups that form the bulk of expansion. It resolves each slot via
 * {@link SlotResolver} (bridge match, then GroupTarget fallback), collecting the emitted bundles. The group is
 * SAT only when every slot resolves; unresolved slots are returned as pending for the next pass.
 */
@RequiredArgsConstructor
final class BridgeExpander implements GroupExpander {

    private final SlotResolver slotResolver;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return !GroupShapes.isPathSegment(group) && !GroupShapes.isDirectiveBinding(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var bundles = new ArrayList<DeltaBundle>();
        final var pendingSlots = new ArrayList<Node>();
        for (final var slot : group.getSlots()) {
            if (!slotResolver.resolve(slot, group, snapshot, bundles)) {
                pendingSlots.add(slot);
            }
        }
        return new GroupStepResult(bundles, pendingSlots);
    }
}
