package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class BridgeSourceToTargetPhase implements ExpansionPhase {

    private final List<Bridge> bridges;
    private final ResolveCtx resolveCtx;

    @Override
    public MapperGraph apply(final MapperGraph graph) {
        final List<Edge> seedEdges = collectFlavorTwoSeedEdges(graph);

        for (final Edge seedEdge : seedEdges) {
            final Node sourceSeed = seedEdge.getFrom();
            final Node targetSeed = seedEdge.getTo();

            final Node realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
            if (realisedSource == null) {
                continue;
            }

            final Node realisedTarget = resolveRealisedCounterpart(targetSeed, graph);
            if (realisedTarget == null) {
                continue;
            }

            final TypeMirror fromType = realisedSource.getType().orElse(null);
            final TypeMirror toType = realisedTarget.getType().orElse(null);

            if (fromType == null || toType == null) {
                continue;
            }

            invokeBridgeStrategies(realisedSource, realisedTarget, fromType, toType, graph);
        }

        return graph;
    }

    private List<Edge> collectFlavorTwoSeedEdges(final MapperGraph graph) {
        final List<Edge> result = new ArrayList<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            final boolean fromSource = edge.getFrom().getLoc() instanceof SourceLocation;
            final boolean toTarget = edge.getTo().getLoc() instanceof TargetLocation;
            if (fromSource && toTarget) {
                result.add(edge);
            }
        }
        return result;
    }

    private void invokeBridgeStrategies(
            final Node realisedSource,
            final Node realisedTarget,
            final TypeMirror fromType,
            final TypeMirror toType,
            final MapperGraph graph) {
        for (final Bridge bridge : bridges) {
            final Optional<BridgeStep> optionalStep = bridge.bridge(fromType, toType, resolveCtx);
            if (!optionalStep.isPresent()) {
                continue;
            }

            final BridgeStep bridgeStep = optionalStep.get();

            final Edge realisedEdge = Edge.realised(
                    realisedSource,
                    realisedTarget,
                    bridgeStep.getWeight(),
                    Optional.empty(),
                    bridgeStep.getCodegen(),
                    bridge.getClass().getName());
            graph.addEdge(realisedEdge);
        }
    }

    @Nullable
    private Node resolveRealisedCounterpart(final Node seedNode, final MapperGraph graph) {
        if (seedNode.getType().isPresent()) {
            return seedNode;
        }

        final Node markerTarget = findTypedMarkerTarget(seedNode, graph);
        if (markerTarget != null) {
            return markerTarget;
        }

        return findTypedRealisedSource(seedNode, graph);
    }

    @Nullable
    private Node findTypedMarkerTarget(final Node seedNode, final MapperGraph graph) {
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.MARKER) {
                continue;
            }
            if (!edge.getFrom().equals(seedNode)) {
                continue;
            }
            final Node target = edge.getTo();
            if (target.getType().isPresent()) {
                return target;
            }
        }
        return null;
    }

    @Nullable
    private Node findTypedRealisedSource(final Node seedNode, final MapperGraph graph) {
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.REALISED) {
                continue;
            }
            if (!edge.getTo().equals(seedNode)) {
                continue;
            }
            final Node source = edge.getFrom();
            if (source.getType().isPresent()) {
                return source;
            }
        }
        return null;
    }
}
