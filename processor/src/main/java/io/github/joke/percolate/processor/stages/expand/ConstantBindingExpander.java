package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a constant-binding seed group ({@code tgt[..]} root over a single {@code ConstantLocation} slot): once the
 * root's declared type is pinned by the consuming assembly, it asks the {@link FrontierMatcher} to type the constant
 * node from that type and realise the literal producer edge into the root (via the {@code ConstantValue} strategy).
 * The group is SAT once that producer edge is in view; until then it stays pending on the root. A constant whose
 * literal cannot be coerced never gets a producer edge and the group goes UNSAT (the late coercion-failure
 * diagnostic owns the message).
 */
@RequiredArgsConstructor
final class ConstantBindingExpander implements GroupExpander {

    private final FrontierMatcher frontierMatcher;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isConstantBinding(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var root = group.getRoot();
        if (!snapshot.viewOf(group).incomingEdgesOf(root).isEmpty()) {
            return new GroupStepResult(List.of(), List.of());
        }
        return new GroupStepResult(frontierMatcher.produceConstant(group, snapshot), List.of(root));
    }
}
