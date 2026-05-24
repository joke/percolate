package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableList;

@RequiredArgsConstructor
public final class ResolveTargetChainsPhase implements ExpansionPhase {

    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        final var rootNodes = findReturnRootNodes(graph);
        for (final var rootNode : rootNodes) {
            final var leafTargets = findLeafTargets(rootNode, graph);
            if (leafTargets.isEmpty()) {
                continue;
            }
            final var targetTails = extractTargetTails(leafTargets);
            if (targetTails.isEmpty()) {
                continue;
            }
            final var returnType = rootNode.getType().orElseThrow();
            deriveForReturnRoot(rootNode, leafTargets, returnType, targetTails, graph);
        }
    }

    private void deriveForReturnRoot(
            final Node rootNode,
            final List<Node> leafTargets,
            final TypeMirror returnType,
            final List<String> targetTails,
            final MapperGraph graph) {
        for (final var strategy : groupTargets) {
            final var optionalBuild = strategy.buildFor(returnType, targetTails, resolveCtx);
            if (!optionalBuild.isPresent()) {
                continue;
            }
            final var groupBuild = optionalBuild.get();
            final var codegen = groupBuild.getCodegen();
            final var strategyFqn = strategy.getClass().getName();
            final var slotNodes = new ArrayList<Node>(groupBuild.getSlots().size());
            final var slotEdges = new HashSet<Edge>(groupBuild.getSlots().size());

            for (final var slot : groupBuild.getSlots()) {
                final var slotNode = obtainOrAllocateSlotNode(leafTargets, slot, rootNode, graph);
                if (slotNode.getType().isEmpty()) {
                    slotNode.setType(slot.getType());
                }
                slotNodes.add(slotNode);
                final var edge = Edge.realised(slotNode, rootNode, slot.getWeight(), codegen::render, strategyFqn);
                if (graph.addEdge(edge)) {
                    slotEdges.add(edge);
                }
            }

            final var group = ExpansionGroup.of(rootNode, slotNodes, codegen, strategyFqn, slotEdges, graph);
            graph.addGroup(group);
        }
    }

    private Node obtainOrAllocateSlotNode(
            final List<Node> leafTargets, final Slot slot, final Node rootNode, final MapperGraph graph) {
        final var existing = findCorrespondingSeedNode(leafTargets, slot.getName());
        if (existing != null) {
            return existing;
        }
        final var slotLoc = new TargetLocation(TargetPath.of(slot.getName()));
        final var fresh = new Node(Optional.empty(), slotLoc, rootNode.getScope());
        graph.addNode(fresh);
        return fresh;
    }

    private List<Node> findReturnRootNodes(final MapperGraph graph) {
        return graph.nodes().filter(this::isReturnRootNode).collect(toUnmodifiableList());
    }

    private boolean isReturnRootNode(final Node node) {
        if (!(node.getLoc() instanceof TargetLocation)) {
            return false;
        }
        final var targetLoc = (TargetLocation) node.getLoc();
        final var isEmpty = targetLoc.getPath().getSegments().isEmpty();
        final var hasType = node.getType().isPresent();
        return isEmpty && hasType;
    }

    private List<Node> findLeafTargets(final Node root, final MapperGraph graph) {
        final List<Node> leaves = new ArrayList<>();
        final Set<Node> visited = new HashSet<>();
        final Deque<Node> queue = new ArrayDeque<>();

        queue.add(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            final var current = queue.poll();
            final var hasIncomingTargetSeed = collectSeedNeighbors(current, graph, visited, queue);
            if (!hasIncomingTargetSeed
                    && !current.equals(root)
                    && current.getType().isEmpty()) {
                leaves.add(current);
            }
        }

        return leaves;
    }

    private boolean collectSeedNeighbors(
            final Node current, final MapperGraph graph, final Set<Node> visited, final Deque<Node> queue) {
        var hasIncomingTargetSeed = false;
        for (final var edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            if (!edge.getTo().equals(current)) {
                continue;
            }
            final var next = edge.getFrom();
            if (visited.contains(next)) {
                continue;
            }
            visited.add(next);
            if (next.getLoc() instanceof TargetLocation) {
                hasIncomingTargetSeed = true;
                queue.add(next);
            }
        }
        return hasIncomingTargetSeed;
    }

    private List<String> extractTargetTails(final List<Node> leafTargets) {
        return leafTargets.stream()
                .map(Node::getLoc)
                .filter(l -> l instanceof TargetLocation)
                .map(l -> (TargetLocation) l)
                .map(tl -> tl.getPath().getSegments())
                .filter(segments -> !segments.isEmpty())
                .map(segments -> segments.get(segments.size() - 1))
                .collect(toUnmodifiableList());
    }

    @Nullable
    private Node findCorrespondingSeedNode(final List<Node> leafTargets, final String slotName) {
        return leafTargets.stream()
                .filter(leaf -> leaf.getLoc() instanceof TargetLocation)
                .filter(leaf -> {
                    final var targetLoc = (TargetLocation) leaf.getLoc();
                    final var segments = targetLoc.getPath().getSegments();
                    return !segments.isEmpty()
                            && segments.get(segments.size() - 1).equals(slotName);
                })
                .findFirst()
                .orElse(null);
    }
}
