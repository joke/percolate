package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.GroupRegistration;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.GroupCodegen;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
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
    public void apply(final MapperGraph graph) {
        final var rootNodes = findReturnRootNodes(graph);

        rootNodes.stream()
                .flatMap(rootNode -> {
                    final var leafTargets = findLeafTargets(rootNode, graph);
                    if (leafTargets.isEmpty()) {
                        return Stream.empty();
                    }

                    final var targetTails = extractTargetTails(leafTargets);
                    if (targetTails.isEmpty()) {
                        return Stream.empty();
                    }

                    final var returnType = rootNode.getType().get();
                    return deriveForReturnRoot(rootNode, leafTargets, returnType, targetTails);
                })
                .forEach(graph::apply);
    }

    private Stream<GraphDelta> deriveForReturnRoot(
            final Node rootNode,
            final List<Node> leafTargets,
            final TypeMirror returnType,
            final List<String> targetTails) {
        return groupTargets.stream().flatMap(strategy -> {
            final var optionalBuild = strategy.buildFor(returnType, targetTails, resolveCtx);
            if (!optionalBuild.isPresent()) {
                return Stream.empty();
            }

            final var groupBuild = optionalBuild.get();
            final var groupId = nextGroupId();
            final GroupCodegen codegen = groupBuild.getCodegen();
            final var registration = new GroupRegistration(groupId, codegen);

            return groupBuild.getSlots().stream().map(slot -> {
                final var slotNode = allocateSlotNode(rootNode, slot);
                final EdgeCodegen slotCodegen = codegen::render;
                final var realisedEdge = Edge.realised(
                        slotNode,
                        rootNode,
                        slot.getWeight(),
                        Optional.of(groupId),
                        slotCodegen,
                        strategy.getClass().getName());

                final var seedNode = findCorrespondingSeedNode(leafTargets, slot.getName());

                final List<Node> nodes = new ArrayList<>(1);
                nodes.add(slotNode);

                final List<Edge> edges = new ArrayList<>(2);
                edges.add(realisedEdge);

                if (seedNode != null) {
                    final var markerEdge =
                            Edge.marker(seedNode, slotNode, strategy.getClass().getName());
                    edges.add(markerEdge);
                }

                return GraphDelta.of(nodes, edges, List.of(registration));
            });
        });
    }

    private String nextGroupId() {
        return "g" + groupIdCounter.incrementAndGet();
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
            final var hasIncomingSeed = collectSeedNeighbors(current, graph, visited, queue);
            if (!hasIncomingSeed && !current.equals(root) && current.getType().isEmpty()) {
                leaves.add(current);
            }
        }

        return leaves;
    }

    private boolean collectSeedNeighbors(
            final Node current, final MapperGraph graph, final Set<Node> visited, final Deque<Node> queue) {
        var hasIncomingSeed = false;
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
                hasIncomingSeed = true;
                queue.add(next);
            }
        }
        return hasIncomingSeed;
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

    private Node allocateSlotNode(final Node rootNode, final Slot slot) {
        final var slotPath = TargetPath.of(slot.getName());
        final var slotLoc = new TargetLocation(slotPath);
        return new Node(Optional.of(slot.getType()), slotLoc, rootNode.getScope(), Optional.empty());
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
