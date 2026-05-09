package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class BridgeSourceToTargetPhase implements ExpansionPhase {

    private final List<Bridge> bridges;
    private final ResolveCtx resolveCtx;

    @Override
    public boolean apply(final MapperGraph graph) {
        boolean anyAdded = false;

        final List<Edge> flavorTwoSeedEdges = collectFlavorTwoSeedEdges(graph);
        for (final Edge seedEdge : flavorTwoSeedEdges) {
            anyAdded |= processSeedEdge(seedEdge, graph);
        }

        final List<Edge> subSeedEdges = collectSubSeedEdges(graph);
        for (final Edge subSeedEdge : subSeedEdges) {
            anyAdded |= processSubSeedEdge(subSeedEdge, graph);
        }

        return anyAdded;
    }

    private boolean processSeedEdge(final Edge seedEdge, final MapperGraph graph) {
        final Node sourceSeed = seedEdge.getFrom();
        final Node targetSeed = seedEdge.getTo();

        final Node realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
        if (realisedSource == null) {
            return false;
        }

        final Node realisedTarget = resolveRealisedCounterpart(targetSeed, graph);
        if (realisedTarget == null) {
            return false;
        }

        final TypeMirror fromType = realisedSource.getType().orElse(null);
        final TypeMirror toType = realisedTarget.getType().orElse(null);

        if (fromType == null || toType == null) {
            return false;
        }

        final Optional<AnnotationMirror> directive = seedEdge.getDirective();
        return invokeBridgeStrategies(realisedSource, realisedTarget, fromType, toType, graph, directive);
    }

    private boolean processSubSeedEdge(final Edge subSeedEdge, final MapperGraph graph) {
        final Node fromNode = subSeedEdge.getFrom();
        final Node toNode = subSeedEdge.getTo();

        final TypeMirror fromType = resolveNodeType(fromNode, graph);
        final TypeMirror toType = resolveNodeType(toNode, graph);

        if (fromType == null || toType == null) {
            return false;
        }

        final Scope scope = fromNode.getScope();
        final io.github.joke.percolate.processor.graph.Location loc = fromNode.getLoc();

        final Node inputNode = findOrCreateNode(scope, loc, fromType, graph);
        final Node outputNode = findOrCreateNode(scope, loc, toType, graph);

        final Optional<AnnotationMirror> directive = subSeedEdge.getDirective();
        return invokeBridgeStrategies(inputNode, outputNode, fromType, toType, graph, directive);
    }

    @Nullable
    private TypeMirror resolveNodeType(final Node node, final MapperGraph graph) {
        if (node.getType().isPresent()) {
            return node.getType().get();
        }
        final Node markerTarget = findTypedMarkerTarget(node, graph);
        if (markerTarget != null && markerTarget.getType().isPresent()) {
            return markerTarget.getType().get();
        }
        final Node realisedSource = findTypedRealisedSource(node, graph);
        if (realisedSource != null && realisedSource.getType().isPresent()) {
            return realisedSource.getType().get();
        }
        return null;
    }

    private boolean invokeBridgeStrategies(
            final Node realisedSource,
            final Node realisedTarget,
            final TypeMirror fromType,
            final TypeMirror toType,
            final MapperGraph graph,
            final Optional<AnnotationMirror> directive) {
        boolean anyAdded = false;
        for (final Bridge bridge : bridges) {
            final List<BridgeStep> steps = bridge.bridge(fromType, toType, resolveCtx).collect(toUnmodifiableList());
            for (final BridgeStep bridgeStep : steps) {
                anyAdded |= applyUnifiedEmissionRule(
                        realisedSource, realisedTarget, bridgeStep, graph, directive, bridge.getClass().getName());
            }
        }
        return anyAdded;
    }

    private boolean applyUnifiedEmissionRule(
            final Node f,
            final Node t,
            final BridgeStep step,
            final MapperGraph graph,
            final Optional<AnnotationMirror> directive,
            final String strategyFqn) {
        final Scope scope = f.getScope();
        final io.github.joke.percolate.processor.graph.Location loc = f.getLoc();

        final Node inputNode;
        if (step.getInputType() != null && f.getType().isPresent()
                && resolveCtx.types().isSameType(step.getInputType(), f.getType().get())) {
            inputNode = f;
        } else {
            inputNode = findOrCreateNode(scope, loc, step.getInputType(), graph);
        }

        final Node outputNode;
        if (step.getOutputType() != null && t.getType().isPresent()
                && resolveCtx.types().isSameType(step.getOutputType(), t.getType().get())) {
            outputNode = t;
        } else {
            outputNode = findOrCreateNode(scope, loc, step.getOutputType(), graph);
        }

        final Edge realisedEdge = Edge.realised(
                inputNode,
                outputNode,
                step.getWeight(),
                Optional.empty(),
                step.getCodegen(),
                strategyFqn);
        boolean addedRealised = graph.addEdge(realisedEdge);

        if (!inputNode.equals(f)) {
            final Edge subSeedEdge = Edge.subSeed(f, inputNode, strategyFqn, directive);
            addedRealised |= graph.addEdge(subSeedEdge);
        }
        return addedRealised;
    }

    private Node findOrCreateNode(
            final Scope scope,
            final io.github.joke.percolate.processor.graph.Location loc,
            final TypeMirror type,
            final MapperGraph graph) {
        for (final Node existing : graph.nodes().collect(toUnmodifiableList())) {
            if (existing.getScope().equals(scope)
                    && existing.getLoc().equals(loc)
                    && resolveCtx.types().isSameType(existing.getType().orElse(null), type)) {
                return existing;
            }
        }
        final Node newNode = new Node(Optional.of(type), loc, scope, Optional.empty());
        graph.addNode(newNode);
        return newNode;
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

    private List<Edge> collectSubSeedEdges(final MapperGraph graph) {
        final List<Edge> result = new ArrayList<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() == EdgeKind.SUB_SEED) {
                result.add(edge);
            }
        }
        return result;
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
