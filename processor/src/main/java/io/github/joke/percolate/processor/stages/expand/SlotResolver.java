package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a single frontier (a group slot, or a directive-binding root) to SAT or pending, appending any
 * expansion bundles it produces. Shared by {@link BridgeExpander} and {@link DirectiveBindingExpander}.
 *
 * <p>A frontier is SAT when it is a parameter-root base case, or when a child sub-group rooted at it is SAT.
 * Otherwise, if it has an effective type and no child group yet, it is expanded — first via bridge matches
 * ({@link FrontierMatcher}), falling back to a {@link GroupTarget} build. Expansion never SATs the frontier in
 * the same pass; SAT arrives once a spawned child SATs in a later pass.
 */
@RequiredArgsConstructor
final class SlotResolver {

    private static final int SINGLE_SEGMENT = 1;

    private final FrontierMatcher frontierMatcher;
    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;

    boolean resolve(
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final List<DeltaBundle> out) {
        if (isParameterRootSlot(frontier, snapshot)) {
            return true;
        }
        if (hasSatChildAt(frontier, snapshot)) {
            return true;
        }
        final var effectiveType = snapshot.effectiveTypeFor(frontier, group);
        if (effectiveType == null) {
            return false;
        }
        if (hasAnyChildAt(frontier, group, snapshot)) {
            return false;
        }
        final var matches = frontierMatcher.matchAt(frontier, group, snapshot);
        if (matches.isEmpty()) {
            tryGroupTargets(frontier, effectiveType, snapshot).ifPresent(out::add);
        } else {
            out.addAll(matches);
        }
        return false;
    }

    boolean isParameterRootSlot(final Node frontier, final ExpansionSnapshot snapshot) {
        if (!(frontier.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var segments = ((SourceLocation) frontier.getLoc()).getPath().getSegments();
        if (segments.size() != SINGLE_SEGMENT) {
            return false;
        }
        final var method = snapshot.currentMethod();
        if (method == null) {
            return snapshot.typeOf(frontier).isPresent();
        }
        return SourceParams.forSlot(frontier, method) != null;
    }

    boolean hasSatChildAt(final Node node, final ExpansionSnapshot snapshot) {
        return snapshot.groups().anyMatch(g -> snapshot.isSat(g) && g.getRoot().equals(node));
    }

    boolean hasAnyChildAt(final Node node, final ExpansionGroup excluding, final ExpansionSnapshot snapshot) {
        return snapshot.groups()
                .anyMatch(g -> !g.equals(excluding) && g.getRoot().equals(node));
    }

    private Optional<DeltaBundle> tryGroupTargets(
            final Node frontier, final TypeMirror frontierType, final ExpansionSnapshot snapshot) {
        for (final var groupTarget : groupTargets) {
            final var build = groupTarget.buildFor(frontierType, List.of(), resolveCtx);
            if (build.isPresent()) {
                return Optional.of(buildGroupTargetBundle(
                        frontier,
                        frontierType,
                        build.get(),
                        groupTarget.getClass().getName(),
                        snapshot));
            }
        }
        return Optional.empty();
    }

    private DeltaBundle buildGroupTargetBundle(
            final Node frontier,
            final TypeMirror frontierType,
            final io.github.joke.percolate.spi.GroupBuild build,
            final String strategyFqn,
            final ExpansionSnapshot snapshot) {
        final var deltas = new ArrayList<Delta>();
        if (snapshot.typeOf(frontier).isEmpty()) {
            deltas.add(new TypeNode(frontier, frontierType, snapshot.currentMethod()));
        }
        final var codegen = build.getCodegen();
        final var slotNodes = new ArrayList<Node>(build.getSlots().size());
        final var slotEdges = new HashSet<Edge>(build.getSlots().size());
        @SuppressWarnings({"PMD.LooseCoupling", "IdentityHashMapUsage"})
        final IdentityHashMap<Node, io.github.joke.percolate.spi.Slot> slotMetadata =
                new IdentityHashMap<>(build.getSlots().size());
        for (final var slot : build.getSlots()) {
            final Location slotLoc = new ElementLocation(slot.getName());
            final var slotNode = new Node(Optional.empty(), slotLoc, frontier.getScope(), Optional.of(frontier));
            deltas.add(new AddNode(slotNode));
            slotNodes.add(slotNode);
            slotMetadata.put(slotNode, slot);
            final var edge = Edge.realised(slotNode, frontier, slot.getWeight(), codegen::render, strategyFqn);
            deltas.add(new AddEdge(edge));
            slotEdges.add(edge);
        }
        deltas.add(new AddGroup(frontier, slotNodes, codegen, strategyFqn, slotEdges, slotMetadata, List.of()));
        return new DeltaBundle(strategyFqn, deltas);
    }
}
