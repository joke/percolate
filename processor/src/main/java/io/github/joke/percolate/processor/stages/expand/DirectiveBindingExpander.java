package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Weights;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Binds a target leaf (root) to a source leaf (slot). The root's type is the declared target type, supplied by
 * its target chain (read via {@code effectiveTypeFor}, which the chain pins onto this group's metadata) — the
 * source slot's type is never stamped onto the root. While the root's target type is unknown the group stays
 * pending. When the resolved source and declared target types match it emits a direct-assign edge (typing the
 * root with the target type at this producer commit) and SATs; otherwise it expands the root as a frontier
 * (bridge / GroupTarget) to convert the source type into the target type, SATing once a spawned child SATs.
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
        final var rootType = snapshot.effectiveTypeFor(root, group);
        if (rootType == null) {
            return new GroupStepResult(deltas, List.of(root));
        }
        final var slotType = snapshot.typeOf(slot);
        if (slotType.isPresent() && resolveCtx.types().isSameType(slotType.get(), rootType)) {
            deltas.add(directAssignEdge(group, slot, root, rootType, snapshot));
            return new GroupStepResult(deltas, List.of());
        }
        if (slotResolver.resolve(root, group, snapshot, deltas)) {
            return new GroupStepResult(deltas, List.of());
        }
        return new GroupStepResult(deltas, List.of(slot));
    }

    private DeltaBundle directAssignEdge(
            final ExpansionGroup group,
            final Node slot,
            final Node root,
            final TypeMirror rootType,
            final ExpansionSnapshot snapshot) {
        final var edge = Edge.realised(slot, root, Weights.NOOP, DirectAssignCodegen.INSTANCE, DIRECT_ASSIGN_FQN);
        final var deltas = new ArrayList<Delta>();
        if (snapshot.typeOf(root).isEmpty()) {
            deltas.add(new TypeNode(root, rootType, snapshot.producerScopeOf(slot)));
        }
        deltas.add(new AddEdge(edge));
        deltas.add(new AddEdgeToView(group, edge));
        return new DeltaBundle(DIRECT_ASSIGN_FQN, deltas);
    }
}
