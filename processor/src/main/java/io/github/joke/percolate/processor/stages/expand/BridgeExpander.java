package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;

/**
 * The fallback expander for any non-seed sub-group spawned during expansion (the BOUNDARY-spawned conversion,
 * method-call, container and constructor sub-groups). It resolves each slot via {@link SlotResolver} (the single
 * strategy round, or a base case), collecting the emitted bundles. The group is SAT only when every slot resolves;
 * unresolved slots are returned as pending for the next pass.
 */
@RequiredArgsConstructor
final class BridgeExpander implements GroupExpander {

    private final SlotResolver slotResolver;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return !GroupShapes.isSourceDescent(group)
                && !GroupShapes.isAssembly(group)
                && !GroupShapes.isDirectiveBinding(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var bundles = new ArrayList<DeltaBundle>();
        final var pendingSlots = new ArrayList<Node>();
        for (final var slot : group.inputs()) {
            if (!slotResolver.resolve(slot, group, snapshot, bundles)) {
                pendingSlots.add(slot);
            }
        }
        slotResolver.expandConversionFrontiers(group, snapshot, bundles);
        return new GroupStepResult(bundles, pendingSlots);
    }
}
