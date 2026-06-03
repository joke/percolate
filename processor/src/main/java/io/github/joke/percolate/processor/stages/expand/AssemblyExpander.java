package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Produces a target-assembly seed group's root (a {@code tgt[..]} node over its child target-leaf slots) by running
 * the single strategy round on the root ({@link FrontierMatcher#matchAt}). An assembly strategy (e.g.
 * {@code ConstructorCall}) sees the constructable target type and emits a multi-slot BOUNDARY whose slots bind, by
 * name, to the pre-seeded child target leaves — opening a non-seed assembly sub-group that carries the constructor
 * codegen. The seed group is SAT once that sub-group is SAT. Unsatisfiable constructions stay UNSAT and are pruned.
 */
@RequiredArgsConstructor
final class AssemblyExpander implements GroupExpander {

    private final FrontierMatcher frontierMatcher;
    private final SlotResolver slotResolver;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isAssembly(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var root = group.getRoot();
        if (slotResolver.hasSatChildAt(root, snapshot)) {
            return new GroupStepResult(List.of(), List.of());
        }
        final var bundles = new ArrayList<DeltaBundle>();
        if (!slotResolver.hasAnyChildAt(root, group, snapshot)) {
            bundles.addAll(frontierMatcher.matchAssembly(root, group, snapshot));
        }
        slotResolver.expandConversionFrontiers(group, snapshot, bundles);
        return new GroupStepResult(bundles, List.of(root));
    }
}
