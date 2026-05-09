package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.spi.GroupBuild;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Slot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ResolveTargetChainsPhase implements ExpansionPhase {

    private static final int INITIAL_GROUP_ID = 0;

    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;
    private final AtomicInteger groupIdCounter = new AtomicInteger(INITIAL_GROUP_ID);

    @Override
    public boolean apply(final MapperGraph graph) {
        final List<Node> rootNodes = findReturnRootNodes(graph);
        boolean anyAdded = false;

        for (final Node rootNode : rootNodes) {
            final List<Node> leafTargets = findLeafTargets(rootNode, graph);
            if (leafTargets.isEmpty()) {
                continue;
            }

            final List<String> targetTails = extractTargetTails(leafTargets);
            if (targetTails.isEmpty()) {
                continue;
            }

            final TypeMirror returnType = rootNode.getType().get();
            anyAdded |= invokeGroupTargetStrategies(rootNode, leafTargets, returnType, targetTails, graph);
        }

        return anyAdded;
    }

    private List<Node> findReturnRootNodes(final MapperGraph graph) {
        final List<Node> result = new ArrayList<>();
        for (final Node node : graph.nodes().collect(toUnmodifiableList())) {
            if (isReturnRootNode(node)) {
                result.add(node);
            }
        }
        return result;
    }

    private boolean isReturnRootNode(final Node node) {
        if (!(node.getLoc() instanceof TargetLocation)) {
            return false;
        }
        final TargetLocation targetLoc = (TargetLocation) node.getLoc();
        final boolean isEmpty = targetLoc.getPath().getSegments().isEmpty();
        final boolean hasType = node.getType().isPresent();
        return isEmpty && hasType;
    }

    private List<Node> findLeafTargets(final Node root, final MapperGraph graph) {
        final List<Node> leaves = new ArrayList<>();
        final Set<Node> visited = new HashSet<>();
        final Deque<Node> queue = new ArrayDeque<>();

        queue.add(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            final Node current = queue.poll();
            boolean hasIncomingSeed = false;

            for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
                if (edge.getKind() != EdgeKind.SEED) {
                    continue;
                }
                if (!edge.getTo().equals(current)) {
                    continue;
                }
                final Node next = edge.getFrom();
                if (visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                if (next.getLoc() instanceof TargetLocation) {
                    hasIncomingSeed = true;
                    queue.add(next);
                }
            }

            if (!hasIncomingSeed && !current.equals(root)) {
                if (!current.getType().isPresent()) {
                    leaves.add(current);
                }
            }
        }

        return leaves;
    }

    private List<String> extractTargetTails(final List<Node> leafTargets) {
        final List<String> tails = new ArrayList<>();
        for (final Node leaf : leafTargets) {
            final TargetLocation targetLoc = (TargetLocation) leaf.getLoc();
            final List<String> segments = targetLoc.getPath().getSegments();
            if (!segments.isEmpty()) {
                tails.add(segments.get(segments.size() - 1));
            }
        }
        return tails;
    }

    private boolean invokeGroupTargetStrategies(
            final Node rootNode,
            final List<Node> leafTargets,
            final TypeMirror returnType,
            final List<String> targetTails,
            final MapperGraph graph) {
        boolean anyAdded = false;
        for (final GroupTarget strategy : groupTargets) {
            final Optional<GroupBuild> optionalBuild = strategy.buildFor(returnType, targetTails, resolveCtx);
            if (!optionalBuild.isPresent()) {
                continue;
            }

            final GroupBuild groupBuild = optionalBuild.get();
            final String groupId = nextGroupId();

            graph.addGroupCodegen(groupId, groupBuild.getCodegen());

            for (final Slot slot : groupBuild.getSlots()) {
                final Node slotNode = allocateSlotNode(rootNode, slot);
                graph.addNode(slotNode);

                final io.github.joke.percolate.processor.graph.EdgeCodegen slotCodegen =
                        groupBuild.getCodegen()::render;
                final Edge realisedEdge = Edge.realised(
                        slotNode,
                        rootNode,
                        slot.getWeight(),
                        Optional.of(groupId),
                        slotCodegen,
                        strategy.getClass().getName());
                anyAdded |= graph.addEdge(realisedEdge);

                final Node seedNode = findCorrespondingSeedNode(leafTargets, slot.getName());
                if (seedNode != null) {
                    final Edge markerEdge =
                            Edge.marker(seedNode, slotNode, strategy.getClass().getName());
                    anyAdded |= graph.addEdge(markerEdge);
                }
            }
        }
        return anyAdded;
    }

    private String nextGroupId() {
        return "g" + groupIdCounter.incrementAndGet();
    }

    private Node allocateSlotNode(final Node rootNode, final Slot slot) {
        final TargetPath slotPath = TargetPath.of(slot.getName());
        final TargetLocation slotLoc = new TargetLocation(slotPath);
        return new Node(Optional.of(slot.getType()), slotLoc, rootNode.getScope(), Optional.empty());
    }

    @Nullable
    private Node findCorrespondingSeedNode(final List<Node> leafTargets, final String slotName) {
        for (final Node leaf : leafTargets) {
            if (!(leaf.getLoc() instanceof TargetLocation)) {
                continue;
            }
            final TargetLocation targetLoc = (TargetLocation) leaf.getLoc();
            final List<String> segments = targetLoc.getPath().getSegments();
            if (!segments.isEmpty() && segments.get(segments.size() - 1).equals(slotName)) {
                return leaf;
            }
        }
        return null;
    }
}
