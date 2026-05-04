package io.github.joke.percolate.processor.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.spi.Step;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ResolveSourceChainsPhase implements ExpansionPhase {

    private final List<SourceStep> sourceSteps;
    private final ResolveCtx resolveCtx;

    @Override
    public MapperGraph apply(final MapperGraph graph) {
        final Set<Edge> processed = new HashSet<>();
        final Deque<Edge> workQueue = new ArrayDeque<>();

        collectSourceSeedEdges(graph, workQueue);

        processSeedEdges(workQueue, processed, graph);

        return graph;
    }

    private void collectSourceSeedEdges(final MapperGraph graph, final Deque<Edge> workQueue) {
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            final Node fromNode = edge.getFrom();
            if (fromNode.getLoc() instanceof SourceLocation) {
                workQueue.add(edge);
            }
        }
    }

    private void processSeedEdges(final Deque<Edge> workQueue, final Set<Edge> processed, final MapperGraph graph) {
        while (!workQueue.isEmpty()) {
            final Edge seedEdge = workQueue.poll();
            if (processed.contains(seedEdge)) {
                continue;
            }
            processed.add(seedEdge);

            final Node sourceNode = seedEdge.getFrom();
            final Node targetNode = seedEdge.getTo();

            if (!(sourceNode.getLoc() instanceof SourceLocation)) {
                continue;
            }

            final TypeMirror sourceType = findSourceType(sourceNode, graph);
            if (sourceType == null) {
                continue;
            }

            if (!(targetNode.getLoc() instanceof SourceLocation)) {
                continue;
            }

            final SourceLocation targetLoc = (SourceLocation) targetNode.getLoc();
            final String pathTail = pathTail(targetLoc);

            invokeSourceStrategies(sourceNode, targetNode, sourceType, pathTail, targetLoc, graph);
        }

        // Fixed-point iteration for same-side ?→? edges
        final Set<Node> resolvedNodes = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            final Set<Edge> untypedSourceEdges = collectUntypedSourceEdges(graph);
            for (final Edge edge : untypedSourceEdges) {
                final Node sourceNode = edge.getFrom();
                if (sourceNode.getType().isPresent()) {
                    continue;
                }
                if (resolvedNodes.contains(sourceNode)) {
                    continue;
                }
                final TypeMirror sourceType = findSourceType(sourceNode, graph);
                if (sourceType == null) {
                    continue;
                }
                final Node targetNode = edge.getTo();
                final SourceLocation targetLoc = (SourceLocation) targetNode.getLoc();
                final String pathTail = pathTail(targetLoc);

                invokeSourceStrategies(sourceNode, targetNode, sourceType, pathTail, targetLoc, graph);
                resolvedNodes.add(sourceNode);
                changed = true;
            }
        }
    }

    private String pathTail(final SourceLocation sourceLoc) {
        final List<String> segments = sourceLoc.getPath().getSegments();
        if (segments.isEmpty()) {
            return "";
        }
        return segments.get(segments.size() - 1);
    }

    private void invokeSourceStrategies(
            final Node sourceNode,
            final Node targetNode,
            final TypeMirror sourceType,
            final String pathTail,
            final SourceLocation sourceLoc,
            final MapperGraph graph) {
        for (final SourceStep strategy : sourceSteps) {
            final java.util.stream.Stream<Step> steps = strategy.stepsFrom(sourceType, pathTail, resolveCtx);
            for (final Step step : steps.collect(toUnmodifiableList())) {
                final Node realisedNode = allocateRealisedNode(sourceNode, sourceLoc, step);
                graph.addNode(realisedNode);

                final Edge realisedEdge = Edge.realised(
                        sourceNode,
                        realisedNode,
                        step.getWeight(),
                        Optional.empty(),
                        step.getCodegen(),
                        strategy.getClass().getName());
                graph.addEdge(realisedEdge);

                final Edge markerEdge = Edge.marker(
                        targetNode, realisedNode, strategy.getClass().getName());
                graph.addEdge(markerEdge);
            }
        }
    }

    private Set<Edge> collectUntypedSourceEdges(final MapperGraph graph) {
        final Set<Edge> result = new HashSet<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            final Node sourceNode = edge.getFrom();
            if (!(sourceNode.getLoc() instanceof SourceLocation)) {
                continue;
            }
            if (sourceNode.getType().isPresent()) {
                continue;
            }
            if (!(edge.getTo().getLoc() instanceof SourceLocation)) {
                continue;
            }
            result.add(edge);
        }
        return result;
    }

    @Nullable
    private TypeMirror findSourceType(final Node node, final MapperGraph graph) {
        if (node.getType().isPresent()) {
            return node.getType().get();
        }

        final TypeMirror markerType = findTypeViaMarker(node, graph);
        if (markerType != null) {
            return markerType;
        }

        return findTypeViaRealised(node, graph);
    }

    @Nullable
    private TypeMirror findTypeViaMarker(final Node node, final MapperGraph graph) {
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.MARKER) {
                continue;
            }
            if (!edge.getFrom().equals(node)) {
                continue;
            }
            final Node target = edge.getTo();
            if (target.getType().isPresent()) {
                return target.getType().get();
            }
        }
        return null;
    }

    @Nullable
    private TypeMirror findTypeViaRealised(final Node node, final MapperGraph graph) {
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.REALISED) {
                continue;
            }
            if (!edge.getTo().equals(node)) {
                continue;
            }
            final Node source = edge.getFrom();
            if (source.getType().isPresent()) {
                return source.getType().get();
            }
        }
        return null;
    }

    private Node allocateRealisedNode(final Node seedNode, final SourceLocation sourceLoc, final Step step) {
        final TypeMirror realisedType = step.getProduces();
        return new Node(Optional.of(realisedType), sourceLoc, seedNode.getScope(), Optional.empty());
    }
}
