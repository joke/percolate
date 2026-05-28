package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import java.util.List;

/**
 * Mutable expansion state. Only the driver ({@link ExpandGroupsPhase}) and the {@link Applier} ever see this
 * interface; expanders receive the read-only {@link ExpansionSnapshot} super-type. Mutations are SAT
 * promotion, pending-slot bookkeeping, and the underlying-graph accessor the applier writes through.
 */
public interface ExpansionState extends ExpansionSnapshot {

    void markSat(ExpansionGroup group);

    void recordPending(ExpansionGroup group, List<Node> slots);

    /** Records SAT/UNSAT outcomes for every group once the outer fixed-point loop terminates. */
    void recordOutcomes(boolean converged);

    MapperGraph underlying();
}
