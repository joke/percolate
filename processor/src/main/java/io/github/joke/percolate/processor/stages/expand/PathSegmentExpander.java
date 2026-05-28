package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import java.util.List;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Types and SATs source-side path-segment groups (a {@code src[bean.value]} root resolved one segment past its
 * {@code src[bean]} slot) by delegating to {@link PathSegmentResolver}s via {@link PathSegmentGroupResolver}.
 * On a match it emits the root's typing, the realised segment edge, and that edge into the group view, then
 * SATs the group. With no match it leaves the slot pending, which the fixed-point loop later records as
 * {@code unsatNoPlan}.
 */
@RequiredArgsConstructor
final class PathSegmentExpander implements GroupExpander {

    private final PathSegmentGroupResolver pathResolver;
    private final io.github.joke.percolate.spi.ResolveCtx resolveCtx;

    @Override
    public boolean appliesTo(final ExpansionGroup group) {
        return GroupShapes.isPathSegment(group);
    }

    @Override
    public GroupStepResult step(final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var slot = group.getSlots().get(0);
        if (snapshot.typeOf(slot).isEmpty()) {
            return new GroupStepResult(List.of(), List.of(slot));
        }
        final var match = pathResolver.resolveFor(group, resolveCtx);
        if (match.isEmpty()) {
            return new GroupStepResult(List.of(), List.of(slot));
        }
        final var segment = match.get().segment;
        final var resolverFqn = match.get().resolverClassName;
        final var root = group.getRoot();
        final var deltas = new java.util.ArrayList<Delta>();
        if (snapshot.typeOf(root).isEmpty()) {
            deltas.add(new TypeNode(root, segment.getReturnType(), asElement(segment.getProducedFrom())));
        }
        final var edge = Edge.realised(slot, root, segment.getWeight(), segment.getCodegen(), resolverFqn);
        deltas.add(new AddEdge(edge));
        deltas.add(new AddEdgeToView(group, edge));
        return new GroupStepResult(List.of(new DeltaBundle(resolverFqn, deltas)), List.of());
    }

    @Nullable
    private static Element asElement(final AnnotatedConstruct construct) {
        return construct instanceof Element ? (Element) construct : null;
    }
}
