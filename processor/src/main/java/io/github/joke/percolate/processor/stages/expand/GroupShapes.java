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

    static final String SEED_PACKAGE_PREFIX = "io.github.joke.percolate.processor.stages.seed.";

    private static final int SINGLE_SLOT = 1;

    boolean isSeed(final ExpansionGroup group) {
        return group.getStrategyClassFqn().startsWith(SEED_PACKAGE_PREFIX);
    }

    boolean isSourceDescent(final ExpansionGroup group) {
        if (!isSeed(group) || group.getSlots().size() != SINGLE_SLOT) {
            return false;
        }
        final var root = group.getRoot();
        final var slot = group.getSlots().get(0);
        if (!(root.getLoc() instanceof SourceLocation) || !(slot.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var rootSegs = ((SourceLocation) root.getLoc()).getPath().getSegments();
        final var slotSegs = ((SourceLocation) slot.getLoc()).getPath().getSegments();
        return rootSegs.size() == slotSegs.size() + 1
                && rootSegs.subList(0, slotSegs.size()).equals(slotSegs);
    }

    boolean isAssembly(final ExpansionGroup group) {
        return isSeed(group)
                && !group.getSlots().isEmpty()
                && group.getRoot().getLoc() instanceof TargetLocation
                && group.getSlots().stream().allMatch(slot -> slot.getLoc() instanceof TargetLocation);
    }

    boolean isDirectiveBinding(final ExpansionGroup group) {
        if (!isSeed(group) || group.getSlots().size() != SINGLE_SLOT) {
            return false;
        }
        return group.getRoot().getLoc() instanceof TargetLocation
                && group.getSlots().get(0).getLoc() instanceof SourceLocation;
    }
}
