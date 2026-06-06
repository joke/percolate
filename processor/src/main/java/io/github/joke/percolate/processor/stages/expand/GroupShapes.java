package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import lombok.experimental.UtilityClass;

/**
 * Structural shape predicates that let the driver dispatch each seed group to its kind-specific handler. The
 * predicates are mutually exclusive by construction, which makes the first-match dispatch unambiguous:
 *
 * <ul>
 *   <li>{@link #isSourceDescent} — a source→source seed group ({@code src[a.b]} root over its {@code src[a]} slot):
 *       resolved by descending one path segment via a path-resolver strategy.</li>
 *   <li>{@link #isAssembly} — a target→target seed group ({@code tgt[..]} root over its child target-leaf slots):
 *       resolved by producing the root via an assembly strategy (e.g. ConstructorCall) bound to those leaves.</li>
 *   <li>{@link #isDirectiveBinding} — a target←source seed group: resolved by descending the source slot and then
 *       producing the target root from it (CONVERSION fold or BOUNDARY).</li>
 * </ul>
 *
 * <p>Everything else (the non-seed sub-groups spawned during expansion) falls through to the bridge handler.
 */
@UtilityClass
class GroupShapes {

    private static final int SINGLE_SLOT = 1;

    boolean isSeed(final ExpansionGroup group) {
        return group.isSeed();
    }

    boolean isSourceDescent(final ExpansionGroup group) {
        final var inputs = group.inputs();
        if (!isSeed(group) || inputs.size() != SINGLE_SLOT) {
            return false;
        }
        final var root = group.getRoot();
        final var slot = inputs.get(0);
        if (!(root.getLoc() instanceof SourceLocation) || !(slot.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var rootSegs = ((SourceLocation) root.getLoc()).getPath().getSegments();
        final var slotSegs = ((SourceLocation) slot.getLoc()).getPath().getSegments();
        return rootSegs.size() == slotSegs.size() + 1
                && rootSegs.subList(0, slotSegs.size()).equals(slotSegs);
    }

    boolean isAssembly(final ExpansionGroup group) {
        final var inputs = group.inputs();
        return isSeed(group)
                && !inputs.isEmpty()
                && group.getRoot().getLoc() instanceof TargetLocation
                && inputs.stream().allMatch(slot -> slot.getLoc() instanceof TargetLocation);
    }

    boolean isDirectiveBinding(final ExpansionGroup group) {
        final var inputs = group.inputs();
        if (!isSeed(group) || inputs.size() != SINGLE_SLOT) {
            return false;
        }
        return group.getRoot().getLoc() instanceof TargetLocation
                && inputs.get(0).getLoc() instanceof SourceLocation;
    }
}
