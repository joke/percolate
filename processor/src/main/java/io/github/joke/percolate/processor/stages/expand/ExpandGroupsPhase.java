package io.github.joke.percolate.processor.stages.expand;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupOutcome;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.ElementSeed;
import io.github.joke.percolate.spi.GroupBuild;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ExpandGroupsPhase implements ExpansionPhase {

    static final int MAX_SLOT_ROUNDS = 64;
    static final int MAX_WORK_LIST_ITERATIONS = 256;

    private final List<Bridge> bridges;
    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        final Deque<ExpansionGroup> workList = new ArrayDeque<>();
        graph.groups().forEach(workList::add);
        drainWorkList(graph, workList);
    }

    private void drainWorkList(final MapperGraph graph, final Deque<ExpansionGroup> workList) {
        var iterations = 0;
        while (!workList.isEmpty()) {
            if (iterations >= MAX_WORK_LIST_ITERATIONS) {
                recordDidNotConverge(graph, workList.removeFirst());
            } else {
                fillGroup(workList.removeFirst(), graph, workList);
            }
            iterations++;
        }
    }

    private void recordDidNotConverge(final MapperGraph graph, final ExpansionGroup unprocessed) {
        final var failingSlot = unprocessed.getSlots().isEmpty()
                ? unprocessed.getRoot()
                : unprocessed.getSlots().get(0);
        graph.recordGroupOutcome(GroupOutcome.unsatDidNotConverge(unprocessed, failingSlot));
    }

    private void fillGroup(final ExpansionGroup group, final MapperGraph graph, final Deque<ExpansionGroup> workList) {
        final var sourceRoots = SourceReachability.sourceParameterRoots(graph);
        for (final var slot : group.getSlots()) {
            final var slotOutcome = resolveSlot(slot, graph, sourceRoots, workList);
            if (slotOutcome != SlotOutcome.SAT) {
                graph.recordGroupOutcome(
                        slotOutcome == SlotOutcome.UNSAT_NO_PLAN
                                ? GroupOutcome.unsatNoPlan(group, slot)
                                : GroupOutcome.unsatDidNotConverge(group, slot));
                return;
            }
        }
        // A group SATs iff its root is also producible. For groups whose initialEdges contain
        // slot→root REALISED edges (ConstructorCall, DirectiveBinding, etc.), this is trivially
        // true once the slots SAT. For element-seed nested groups (registerElementSeedGroup
        // emits no slot→root edge), the driver materialises the slot→root chain via the same
        // target-driven bridge expansion used for slots.
        final var rootOutcome = resolveSlot(group.getRoot(), graph, sourceRoots, workList);
        if (rootOutcome != SlotOutcome.SAT) {
            graph.recordGroupOutcome(
                    rootOutcome == SlotOutcome.UNSAT_NO_PLAN
                            ? GroupOutcome.unsatNoPlan(group, group.getRoot())
                            : GroupOutcome.unsatDidNotConverge(group, group.getRoot()));
            return;
        }
        graph.recordGroupOutcome(GroupOutcome.sat(group));
    }

    private enum SlotOutcome {
        SAT,
        UNSAT_NO_PLAN,
        UNSAT_DID_NOT_CONVERGE
    }

    private SlotOutcome resolveSlot(
            final Node slot,
            final MapperGraph graph,
            final Set<Node> sourceRoots,
            final Deque<ExpansionGroup> workList) {
        if (SourceReachability.slotReachable(slot, graph, sourceRoots)) {
            return SlotOutcome.SAT;
        }
        var frontier = List.of(slot);
        for (var round = 1; round <= MAX_SLOT_ROUNDS; round++) {
            final var newNodes = new ArrayList<Node>();
            for (final var f : frontier) {
                expandFrontier(f, graph, workList, newNodes);
            }
            if (SourceReachability.slotReachable(slot, graph, sourceRoots)) {
                return SlotOutcome.SAT;
            }
            if (newNodes.isEmpty()) {
                return SlotOutcome.UNSAT_NO_PLAN;
            }
            frontier = newNodes;
        }
        return SlotOutcome.UNSAT_DID_NOT_CONVERGE;
    }

    private void expandFrontier(
            final Node frontierNode,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList,
            final List<Node> newNodes) {
        final var frontierType = frontierNode.getType().orElse(null);
        if (frontierType == null) {
            return;
        }
        final var candidates = SourceReachability.candidateInputs(frontierNode.getScope(), graph).stream()
                .filter(n -> !n.equals(frontierNode))
                .collect(Collectors.toUnmodifiableList());
        if (tryBridges(frontierNode, frontierType, candidates, graph, workList, newNodes)) {
            return;
        }
        tryGroupTargets(frontierNode, frontierType, graph, workList);
    }

    private boolean tryBridges(
            final Node frontierNode,
            final TypeMirror frontierType,
            final List<Node> candidates,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList,
            final List<Node> newNodes) {
        for (final var bridge : bridges) {
            for (final var candidate : candidates) {
                final var match = tryBridgeOnCandidate(bridge, candidate, frontierNode, frontierType, graph, workList);
                if (match.matched) {
                    if (match.allocated != null) {
                        newNodes.add(match.allocated);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private BridgeMatch tryBridgeOnCandidate(
            final Bridge bridge,
            final Node candidate,
            final Node frontierNode,
            final TypeMirror frontierType,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        final var candidateType = candidate.getType().orElse(null);
        if (candidateType == null) {
            return BridgeMatch.noMatch();
        }
        final var steps = bridge.bridge(candidateType, frontierType, resolveCtx).collect(Collectors.toList());
        for (final var step : steps) {
            if (resolveCtx.types().isSameType(step.getOutputType(), frontierType)) {
                final var allocated = commitBridgeStep(
                        graph, frontierNode, candidate, step, bridge.getClass().getName(), workList);
                return new BridgeMatch(true, allocated);
            }
        }
        return BridgeMatch.noMatch();
    }

    private boolean tryGroupTargets(
            final Node frontierNode,
            final TypeMirror frontierType,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        for (final var groupTarget : groupTargets) {
            final var build = groupTarget.buildFor(frontierType, List.of(), resolveCtx);
            if (build.isPresent()) {
                registerNestedGroup(
                        frontierNode, build.get(), groupTarget.getClass().getName(), graph, workList);
                return true;
            }
        }
        return false;
    }

    private static final class BridgeMatch {
        final boolean matched;
        final @Nullable Node allocated;

        BridgeMatch(final boolean matched, final @Nullable Node allocated) {
            this.matched = matched;
            this.allocated = allocated;
        }

        static BridgeMatch noMatch() {
            return new BridgeMatch(false, null);
        }
    }

    private @Nullable Node commitBridgeStep(
            final MapperGraph graph,
            final Node frontierNode,
            final Node candidate,
            final BridgeStep step,
            final String strategyFqn,
            final Deque<ExpansionGroup> workList) {
        final var allocation = allocateOrReuseInputNode(graph, frontierNode, candidate, step);
        final var inputNode = allocation.node;
        final var fresh = allocation.fresh ? inputNode : null;
        if (inputNode.equals(frontierNode)) {
            return fresh;
        }
        graph.addEdge(Edge.realised(inputNode, frontierNode, step.getWeight(), step.getCodegen(), strategyFqn));
        for (final var elementSeed : step.getElementSeeds()) {
            registerElementSeedGroup(elementSeed, inputNode, step.getWeight(), strategyFqn, graph, workList);
        }
        return fresh;
    }

    private InputAllocation allocateOrReuseInputNode(
            final MapperGraph graph, final Node frontierNode, final Node candidate, final BridgeStep step) {
        final var inputTypeMatches = candidate.getType().isPresent()
                && resolveCtx
                        .types()
                        .isSameType(step.getInputType(), candidate.getType().get());
        if (inputTypeMatches) {
            return new InputAllocation(candidate, false);
        }
        final var fresh = new Node(Optional.of(step.getInputType()), candidate.getLoc(), frontierNode.getScope());
        graph.addNode(fresh);
        return new InputAllocation(fresh, true);
    }

    private static final class InputAllocation {
        final Node node;
        final boolean fresh;

        InputAllocation(final Node node, final boolean fresh) {
            this.node = node;
            this.fresh = fresh;
        }
    }

    private void registerElementSeedGroup(
            final ElementSeed elementSeed,
            final Node parentInputCandidate,
            final int parentWeight,
            final String strategyFqn,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        final Location elementLoc = new ElementLocation(elementSeed.getRole());
        final var scope = parentInputCandidate.getScope();
        final var elemFrom = new Node(Optional.of(elementSeed.getInputType()), elementLoc, scope);
        final var elemTo = new Node(Optional.of(elementSeed.getOutputType()), elementLoc, scope);
        graph.addNode(elemFrom);
        graph.addNode(elemTo);
        graph.addEdge(Edge.realised(
                parentInputCandidate,
                elemFrom,
                parentWeight,
                (vars, inputs) -> CodeBlock.of("$L", inputs.single()),
                strategyFqn));
        final var nested = ExpansionGroup.of(
                elemTo,
                List.of(elemFrom),
                (vars, inputs) -> CodeBlock.of("/* element seed */"),
                strategyFqn,
                Set.of(),
                graph);
        graph.addGroup(nested);
        workList.add(nested);
    }

    private void registerNestedGroup(
            final Node root,
            final GroupBuild build,
            final String strategyFqn,
            final MapperGraph graph,
            final Deque<ExpansionGroup> workList) {
        final var slotNodes = new ArrayList<Node>(build.getSlots().size());
        final var slotEdges = new HashSet<Edge>(build.getSlots().size());
        final var codegen = build.getCodegen();
        for (final var slot : build.getSlots()) {
            final Location slotLoc = new ElementLocation(slot.getName());
            final var slotNode = new Node(Optional.of(slot.getType()), slotLoc, root.getScope());
            graph.addNode(slotNode);
            slotNodes.add(slotNode);
            final var edge = Edge.realised(slotNode, root, slot.getWeight(), codegen::render, strategyFqn);
            graph.addEdge(edge);
            slotEdges.add(edge);
        }
        final var nested = ExpansionGroup.of(root, slotNodes, codegen, strategyFqn, slotEdges, graph);
        graph.addGroup(nested);
        workList.add(nested);
    }
}
