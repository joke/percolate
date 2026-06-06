package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Types and SATs a source-side path-segment seed group (a {@code src[bean.value]} root resolved one segment past
 * its {@code src[bean]} slot) by descending through the single strategy round ({@link FrontierMatcher#descend}).
 * The driver feeds the appended segment as a synthetic directive; a path-resolver strategy emits the BOUNDARY step
 * that opens the descent sub-group binding the parent slot to the root. With no resolvable segment the root stays
 * pending, which the fixed-point loop later records as {@code unsatNoPlan}.
 */
@RequiredArgsConstructor
final class SourceDescentExpander implements GroupExpander {

    private final FrontierMatcher frontierMatcher;
    private final SlotResolver slotResolver;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isSourceDescent(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var slot = group.inputs().get(0);
        final var root = group.getRoot();
        if (snapshot.typeOf(slot).isEmpty()) {
            return new GroupStepResult(List.of(), List.of(slot));
        }
        if (slotResolver.hasSatChildAt(root, snapshot)) {
            return new GroupStepResult(List.of(), List.of());
        }
        if (slotResolver.hasAnyChildAt(root, group, snapshot)) {
            return new GroupStepResult(List.of(), List.of(root));
        }
        return new GroupStepResult(frontierMatcher.descend(group, snapshot), List.of(root));
    }
}
