package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.GroupCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Matches a frontier node against the registered {@link Bridge}s, producing one atomic {@link DeltaBundle}
 * per matching bridge (multi-fire siblings). Candidate inputs come only from the current group's view — never
 * a global scan — which, with instance-identity nodes and narrow boundary import, keeps sibling-derived nodes
 * out of the candidate set. Cycle-closing inverse-bridge matches are still emitted and rejected later by the
 * {@link Applier}'s bundle cycle check.
 */
@RequiredArgsConstructor
final class FrontierMatcher {

    private final List<Bridge> bridges;
    private final InputAllocator inputAllocator;
    private final ResolveCtx resolveCtx;

    List<DeltaBundle> matchAt(final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        final var frontierType = snapshot.effectiveTypeFor(frontier, group);
        if (frontierType == null) {
            return List.of();
        }
        final var candidates = candidatesFrom(frontier, group, snapshot);
        final var bundles = new ArrayList<DeltaBundle>();
        for (final var bridge : bridges) {
            for (final var candidate : candidates) {
                final var matched = tryBridge(bridge, candidate, frontier, frontierType, group, snapshot);
                if (!matched.isEmpty()) {
                    bundles.addAll(matched);
                    break;
                }
            }
        }
        return bundles;
    }

    private List<Node> candidatesFrom(
            final Node frontier, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> !node.equals(frontier))
                .filter(node -> !(node.getLoc() instanceof TargetLocation))
                .filter(node -> node.getType().isPresent())
                .sorted(Comparator.comparing(Node::id))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Emits one bundle per matching {@link BridgeStep}. A single {@link Bridge} (e.g. a container that supplies
     * collect/wrap/iterate in one class) may yield several steps for the same frontier; each becomes its own
     * multi-fire sibling sub-group, exactly as separate per-op bridges did before consolidation.
     */
    private List<DeltaBundle> tryBridge(
            final Bridge bridge,
            final Node candidate,
            final Node frontier,
            final TypeMirror frontierType,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        final var candidateType = candidate.getType().orElseThrow();
        final var steps = bridge.bridge(candidateType, frontierType, resolveCtx).collect(Collectors.toList());
        final var matched = new ArrayList<DeltaBundle>();
        for (final var step : steps) {
            if (!resolveCtx.types().isSameType(step.getOutputType(), frontierType) || !scopeMatches(step, frontier)) {
                continue;
            }
            final var allocation = inputAllocator.allocate(step, frontier, group, snapshot);
            if (!allocation.getNode().equals(frontier)) {
                matched.add(buildBundle(
                        frontier, step, allocation, bridge.getClass().getName(), group, snapshot));
            }
        }
        return matched;
    }

    private DeltaBundle buildBundle(
            final Node frontier,
            final BridgeStep step,
            final InputAllocation allocation,
            final String bridgeFqn,
            final ExpansionGroup group,
            final ExpansionSnapshot snapshot) {
        final var deltas = new ArrayList<Delta>();
        if (allocation.getAddNode() != null) {
            deltas.add(allocation.getAddNode());
        }
        final var input = allocation.getNode();
        final var codegen = step.getCodegen();
        final Edge edge;
        final GroupCodegen groupCodegen;
        if (codegen instanceof EdgeCodegen) {
            final var scalar = (EdgeCodegen) codegen;
            edge = Edge.realised(input, frontier, step.getWeight(), scalar, bridgeFqn);
            groupCodegen = scalar::render;
        } else {
            // Container step: the realised edge carries the provider + its scope transition; the composer renders
            // it as a single-edge container case off the edge. The group's codegen is a dead passthrough.
            edge = Edge.realised(input, frontier, step.getWeight(), codegen, step.getScopeTransition(), bridgeFqn);
            groupCodegen = (vars, inputs) -> inputs.single();
        }
        deltas.add(new AddEdge(edge));
        if (snapshot.typeOf(frontier).isEmpty()) {
            deltas.add(new TypeNode(frontier, step.getOutputType(), snapshot.currentMethod()));
        }
        deltas.add(new AddGroup(
                frontier,
                List.of(input),
                groupCodegen,
                bridgeFqn,
                Set.of(edge),
                Map.of(),
                boundaryImports(frontier, input, group, snapshot)));
        return new DeltaBundle(bridgeFqn, deltas);
    }

    private List<Node> boundaryImports(
            final Node frontier, final Node input, final ExpansionGroup group, final ExpansionSnapshot snapshot) {
        return snapshot.viewOf(group).vertexSet().stream()
                .filter(node -> node.getLoc() instanceof SourceLocation)
                .filter(node -> !node.equals(frontier) && !node.equals(input))
                .sorted(Comparator.comparing(Node::id))
                .collect(Collectors.toUnmodifiableList());
    }

    private static boolean scopeMatches(final BridgeStep step, final Node frontier) {
        switch (step.getScopeTransition()) {
            case PRESERVING:
                return true;
            case ENTERING:
                return frontier.getLoc() instanceof ElementLocation;
            case EXITING:
                return !(frontier.getLoc() instanceof ElementLocation);
        }
        throw new IllegalStateException("Unknown scope transition: " + step.getScopeTransition());
    }
}
