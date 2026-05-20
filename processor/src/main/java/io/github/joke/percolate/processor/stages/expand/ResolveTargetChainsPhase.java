package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.graph.Weights;
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
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ResolveTargetChainsPhase implements ExpansionPhase {

    private static final String DIRECTIVE_BINDING_FQN =
            "io.github.joke.percolate.processor.stages.expand.DirectiveBinding";
    private static final EdgeCodegen PASS_THROUGH_CODEGEN = (vars, inputs) -> CodeBlock.of("$L", inputs.single());
    private static final GroupCodegen PASS_THROUGH_GROUP_CODEGEN =
            (vars, inputs) -> CodeBlock.of("$L", inputs.single());

    private final List<GroupTarget> groupTargets;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        final var rootNodes = findReturnRootNodes(graph);

        // Two passes: first add nodes & edges to the graph; then register groups (groups need
        // their root/slots/edges to already exist in the underlying graph).
        final var pendingGroups = new ArrayList<PendingGroup>();
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
                    return deriveForReturnRoot(rootNode, leafTargets, returnType, targetTails, graph, pendingGroups);
                })
                .forEach(graph::apply);

        for (final var pending : pendingGroups) {
            final var group = ExpansionGroup.of(
                    pending.root, pending.slots, pending.codegen, pending.strategyFqn, pending.slotEdges, graph);
            graph.addGroup(group);
        }
    }

    private Stream<GraphDelta> deriveForReturnRoot(
            final Node rootNode,
            final List<Node> leafTargets,
            final TypeMirror returnType,
            final List<String> targetTails,
            final MapperGraph graph,
            final List<PendingGroup> pendingGroups) {
        return groupTargets.stream().flatMap(strategy -> {
            final var optionalBuild = strategy.buildFor(returnType, targetTails, resolveCtx);
            if (!optionalBuild.isPresent()) {
                return Stream.empty();
            }

            final var groupBuild = optionalBuild.get();
            final var codegen = groupBuild.getCodegen();
            final var strategyFqn = strategy.getClass().getName();

            final var slotNodes = new ArrayList<Node>(groupBuild.getSlots().size());
            final var slotEdges = new HashSet<Edge>(groupBuild.getSlots().size());
            final var deltas = new ArrayList<GraphDelta>(groupBuild.getSlots().size());

            for (final var slot : groupBuild.getSlots()) {
                final var slotNode = allocateSlotNode(rootNode, slot);
                final var realisedEdge =
                        Edge.realised(slotNode, rootNode, slot.getWeight(), codegen::render, strategyFqn);
                slotNodes.add(slotNode);
                slotEdges.add(realisedEdge);

                final var seedNode = findCorrespondingSeedNode(leafTargets, slot.getName());
                final var nodes = new ArrayList<Node>(1);
                nodes.add(slotNode);
                final var edges = new ArrayList<Edge>(3);
                edges.add(realisedEdge);
                if (seedNode != null) {
                    edges.add(Edge.marker(seedNode, slotNode, strategyFqn));
                    addDirectiveBindingIfApplicable(seedNode, slotNode, slot.getType(), graph, edges, pendingGroups);
                }
                deltas.add(GraphDelta.of(nodes, edges, List.of()));
            }

            pendingGroups.add(new PendingGroup(rootNode, slotNodes, codegen, strategyFqn, slotEdges));
            return deltas.stream();
        });
    }

    private void addDirectiveBindingIfApplicable(
            final Node seedNode,
            final Node slotNode,
            final TypeMirror slotType,
            final MapperGraph graph,
            final List<Edge> edges,
            final List<PendingGroup> pendingGroups) {
        final var typedSource = findTypedSeedSource(seedNode, graph);
        if (typedSource.isEmpty()) {
            return;
        }
        final var source = typedSource.get();
        if (!resolveCtx.types().isSameType(source.getType().get(), slotType)) {
            return;
        }
        final var directiveEdge =
                Edge.realised(source, slotNode, Weights.STEP, PASS_THROUGH_CODEGEN, DIRECTIVE_BINDING_FQN);
        edges.add(directiveEdge);
        pendingGroups.add(new PendingGroup(
                slotNode, List.of(source), PASS_THROUGH_GROUP_CODEGEN, DIRECTIVE_BINDING_FQN, Set.of(directiveEdge)));
    }

    private Optional<Node> findTypedSeedSource(final Node seedNode, final MapperGraph graph) {
        final var typedSources = graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getTo().equals(seedNode))
                .map(Edge::getFrom)
                .filter(n -> n.getLoc() instanceof SourceLocation)
                .filter(n -> n.getType().isPresent())
                .collect(toUnmodifiableList());
        return typedSources.size() == 1 ? Optional.of(typedSources.get(0)) : Optional.empty();
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
        return new Node(Optional.of(slot.getType()), slotLoc, rootNode.getScope());
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

    private static final class PendingGroup {
        final Node root;
        final List<Node> slots;
        final GroupCodegen codegen;
        final String strategyFqn;
        final Set<Edge> slotEdges;

        PendingGroup(
                final Node root,
                final List<Node> slots,
                final GroupCodegen codegen,
                final String strategyFqn,
                final Set<Edge> slotEdges) {
            this.root = root;
            this.slots = slots;
            this.codegen = codegen;
            this.strategyFqn = strategyFqn;
            this.slotEdges = slotEdges;
        }
    }
}
