package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;

/**
 * A pure, group-kind-specific expansion strategy. Implementations recognise their group kind structurally via
 * {@link #appliesTo} and describe one pass of progress via {@link #step} — returning data ({@link
 * GroupStepResult}) only, never mutating the graph, the group, or the snapshot.
 *
 * <p>The driver injects a {@code List<GroupExpander>} and dispatches each non-SAT group to the first expander
 * whose {@code appliesTo} returns true. The {@code appliesTo} predicates are mutually exclusive by
 * construction: path-segment, directive-binding, and bridge-group shapes do not overlap.
 */
public interface GroupExpander {

    boolean appliesTo(ExpansionGroup group);

    GroupStepResult step(ExpansionGroup group, ExpansionSnapshot snapshot);
}
