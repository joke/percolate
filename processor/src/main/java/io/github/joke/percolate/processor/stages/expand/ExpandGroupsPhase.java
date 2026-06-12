package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The expansion driver: a cross-group fixed-point loop over registered {@link ExpansionGroup}s. Each outer
 * pass dispatches every non-SAT group to the first applicable {@link GroupExpander} against a single snapshot,
 * collects the emitted {@link DeltaBundle}s, applies them through the {@link Applier} at end of pass, and
 * checks for progress. The loop terminates when a pass applies no deltas and promotes no group to SAT, or when
 * {@link #MAX_OUTER_PASSES} is tripped (a bug signal); outcomes are then drained onto the graph.
 *
 * <p>All graph mutation lives in the {@link Applier}; all expansion logic lives in the {@link GroupExpander}s.
 * This class only orchestrates.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ExpandGroupsPhase implements ExpansionPhase {

    static final int MAX_OUTER_PASSES = 32;

    private final List<GroupExpander> expanders;
    private final Applier applier;

    public static ExpandGroupsPhase create(
            final List<ExpansionStrategy> strategies,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        final var applier = new Applier(nullabilityResolver);
        final var inputAllocator = new InputAllocator(resolveCtx);
        final var frontierMatcher = new FrontierMatcher(strategies, inputAllocator, resolveCtx);
        final var slotResolver = new SlotResolver(frontierMatcher);
        final List<GroupExpander> expanders = List.of(
                new SourceDescentExpander(frontierMatcher, slotResolver),
                new AssemblyExpander(frontierMatcher, slotResolver),
                new DirectiveBindingExpander(slotResolver, frontierMatcher),
                new ConstantBindingExpander(frontierMatcher),
                new BridgeExpander(slotResolver));
        return new ExpandGroupsPhase(expanders, applier);
    }

    @Override
    public void apply(final MapperGraph graph) {
        applier.reset();
        final var state = new ExpansionStateImpl(graph, applier);
        state.recordOutcomes(runFixedPoint(state));
    }

    private boolean runFixedPoint(final ExpansionState state) {
        for (var pass = 0; pass < MAX_OUTER_PASSES; pass++) {
            if (!runPass(state)) {
                return true;
            }
        }
        return false;
    }

    private boolean runPass(final ExpansionState state) {
        final var pending = state.groups().filter(group -> !state.isSat(group)).collect(toUnmodifiableList());
        final var bundles = new ArrayList<DeltaBundle>();
        final var newlySat = new ArrayList<ExpansionGroup>();
        for (final var group : pending) {
            final var result = dispatch(group).step(group, state);
            bundles.addAll(result.getBundles());
            if (result.getPendingSlots().isEmpty()) {
                newlySat.add(group);
            } else {
                state.recordPending(group, result.getPendingSlots());
            }
        }
        final var applied = applier.apply(state, bundles);
        newlySat.forEach(state::markSat);
        return applied > 0 || !newlySat.isEmpty();
    }

    private GroupExpander dispatch(final ExpansionGroup group) {
        return expanders.stream()
                .filter(expander -> expander.appliesTo(group))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No expander applies to group rooted at "
                        + group.getRoot().id()));
    }
}
