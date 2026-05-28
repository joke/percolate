package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Weights;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Binds a target leaf (root) to a source leaf (slot). Once the source slot is satisfied it propagates the
 * slot's type to the root; when their types match it emits a direct-assign edge and SATs; otherwise it expands
 * the root as a frontier (bridge / GroupTarget) to convert the source type into the target type, SATing once a
 * spawned child SATs. Type propagation and the subsequent root resolution land in successive passes, since a
 * pass observes only the previous pass's applied deltas.
 */
@RequiredArgsConstructor
final class DirectiveBindingExpander implements GroupExpander {

    private static final String DIRECT_ASSIGN_FQN = "io.github.joke.percolate.processor.stages.expand.DirectiveBinding";

    private final SlotResolver slotResolver;
    private final ResolveCtx resolveCtx;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isDirectiveBinding(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var slot = group.getSlots().get(0);
        final var deltas = new ArrayList<DeltaBundle>();
        if (!slotResolver.resolve(slot, group, snapshot, deltas)) {
            return new GroupStepResult(deltas, List.of(slot));
        }
        final var root = group.getRoot();
        final var slotType = snapshot.typeOf(slot);
        if (snapshot.typeOf(root).isEmpty()) {
            slotType.ifPresent(type -> deltas.add(directAssignTyping(root, slot, type, snapshot)));
            return new GroupStepResult(deltas, List.of(root));
        }
        final var rootType = snapshot.typeOf(root).orElseThrow();
        if (slotType.isPresent() && resolveCtx.types().isSameType(slotType.get(), rootType)) {
            deltas.add(directAssignEdge(group, slot, root));
            return new GroupStepResult(deltas, List.of());
        }
        if (slotResolver.resolve(root, group, snapshot, deltas)) {
            return new GroupStepResult(deltas, List.of());
        }
        return new GroupStepResult(deltas, List.of(slot));
    }

    private DeltaBundle directAssignTyping(
            final Node root,
            final Node slot,
            final javax.lang.model.type.TypeMirror type,
            final ExpansionSnapshot snapshot) {
        return new DeltaBundle(DIRECT_ASSIGN_FQN, List.of(new TypeNode(root, type, snapshot.producerScopeOf(slot))));
    }

    private DeltaBundle directAssignEdge(final ExpansionGroup group, final Node slot, final Node root) {
        final var edge = Edge.realised(slot, root, Weights.NOOP, DirectAssignCodegen.INSTANCE, DIRECT_ASSIGN_FQN);
        return new DeltaBundle(DIRECT_ASSIGN_FQN, List.of(new AddEdge(edge), new AddEdgeToView(group, edge)));
    }
}
