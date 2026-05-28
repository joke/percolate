package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import lombok.experimental.UtilityClass;

/**
 * Structural shape predicates that let the three expanders recognise their group kind. The predicates are
 * mutually exclusive by construction, which is what makes the driver's first-match dispatch unambiguous: a
 * path-segment group is source→source, a directive-binding group is target→source, and everything else falls
 * through to the bridge expander.
 */
@UtilityClass
class GroupShapes {

    private static final int SINGLE_SLOT = 1;

    boolean isPathSegment(final ExpansionGroup group) {
        return PathSegmentGroupResolver.isPathSegmentGroup(group);
    }

    boolean isDirectiveBinding(final ExpansionGroup group) {
        if (group.getSlots().size() != SINGLE_SLOT) {
            return false;
        }
        return group.getRoot().getLoc() instanceof TargetLocation
                && group.getSlots().get(0).getLoc() instanceof SourceLocation;
    }
}
