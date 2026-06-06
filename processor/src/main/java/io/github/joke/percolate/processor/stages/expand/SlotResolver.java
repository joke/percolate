package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a single frontier (a group slot, or a directive-binding root) to SAT or pending, appending any
 * expansion bundles it produces. Shared by the kind-specific expanders.
 *
 * <p>A frontier is SAT when it is a parameter-root base case, when a realised edge already feeds it inside the
 * group's view (a folded CONVERSION edge), or when a child sub-group rooted at it is SAT. Otherwise, if it has an
 * effective type and no child group yet, it is expanded through the single {@link FrontierMatcher} strategy round.
 * Expansion never SATs the frontier in the same pass; SAT arrives once a folded edge or a spawned child lands.
 */
@RequiredArgsConstructor
final class SlotResolver {

    private final FrontierMatcher frontierMatcher;

    boolean resolve(
            final Node frontier,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot,
            final List<DeltaBundle> out) {
        if (reachable(frontier, group, snapshot)) {
            return true;
        }
        final var effectiveType = snapshot.effectiveTypeFor(frontier, group);
        if (effectiveType == null) {
            return false;
        }
        if (hasAnyChildAt(frontier, group, snapshot)) {
            return false;
        }
        // Expand-once guard: a frontier already fed by realised (conversion-fold) edges has had its producers
        // enumerated. Re-running matchAt would re-emit the same bundles, which the Applier re-applies as no-ops
        // yet counts as progress — preventing the fixed-point loop from ever converging (design E2/E3).
        if (!snapshot.viewOf(group).incomingEdgesOf(frontier).isEmpty()) {
            return false;
        }
        out.addAll(frontierMatcher.matchAt(frontier, group, snapshot));
        return false;
    }

    boolean isParameterRootSlot(final Node frontier) {
        // Real param roots resolve against the node's own MethodScope. With no MethodScope (a synthetic scope, e.g.
        // in isolated unit graphs) a typed single-segment source has no further source chain, so it is terminal —
        // matching the prior behaviour when no current method was set.
        return SourceParams.forSlot(frontier) != null
                || (!(frontier.getScope() instanceof MethodScope) && isTypedSingleSegmentSource(frontier));
    }

    private static boolean isTypedSingleSegmentSource(final Node frontier) {
        return frontier.getLoc() instanceof SourceLocation
                && frontier.getType().isPresent()
                && ((SourceLocation) frontier.getLoc()).getPath().getSegments().size() == 1;
    }

    /**
     * Base-case reachability (design E3): a node is satisfied iff it is a parameter-root base case, a boundary
     * child sub-group rooted at it is SAT, or it has an incoming realised view edge whose source is itself
     * reachable (transitively to a base case). This replaces the former first-incoming-edge rule, so a folded
     * conversion chain {@code X→Y→Z} satisfies {@code Z} only once a complete realised path from a base case
     * exists; an intermediate fed by an unproduced source does not prematurely satisfy.
     */
    boolean reachable(final Node node, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return reachable(node, group, snapshot, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * Expands the group's conversion frontiers so their own producers are discovered. In the dissolved model a
     * conversion frontier is derived from the graph rather than stored on the group (design D5): it is any node in
     * the group's view that is neither the {@code root} nor a demand input — a synthesized conversion intermediate
     * or imported source context tagged into the group. Their resolution is not AND-required for group SAT (an
     * unreachable one is a retained dead end), so the boolean result is intentionally ignored; an already-reachable
     * boundary import resolves trivially.
     */
    void expandConversionFrontiers(
            final ExpansionGroup group, final ExpansionSnapshot snapshot, final List<DeltaBundle> out) {
        final var root = group.getRoot();
        final var inputs = new HashSet<>(group.inputs());
        for (final var frontier : List.copyOf(snapshot.viewOf(group).vertexSet())) {
            if (frontier.equals(root) || inputs.contains(frontier)) {
                continue;
            }
            resolve(frontier, group, snapshot, out);
        }
    }

    private boolean reachable(
            final Node node, final ExpansionGroup group, final ExpansionSnapshot snapshot, final Set<Node> visiting) {
        if (!visiting.add(node)) {
            return false;
        }
        if (isParameterRootSlot(node) || hasSatChildAt(node, snapshot)) {
            return true;
        }
        return snapshot.viewOf(group).incomingEdgesOf(node).stream()
                .anyMatch(edge -> reachable(edge.getFrom(), group, snapshot, visiting));
    }

    boolean hasSatChildAt(final Node node, final ExpansionSnapshot snapshot) {
        return snapshot.groups().anyMatch(g -> snapshot.isSat(g) && g.getRoot().equals(node));
    }

    boolean hasAnyChildAt(final Node node, final ExpansionGroup excluding, final ExpansionSnapshot snapshot) {
        return snapshot.groups()
                .anyMatch(g -> !g.equals(excluding) && g.getRoot().equals(node));
    }
}
