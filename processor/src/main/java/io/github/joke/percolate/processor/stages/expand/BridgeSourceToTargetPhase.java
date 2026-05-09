package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class BridgeSourceToTargetPhase implements ExpansionPhase {

    private final List<Bridge> bridges;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        seeds(graph).flatMap(seed -> derive(seed, graph)).forEach(graph::apply);
    }

    private Stream<Edge> seeds(final MapperGraph graph) {
        final List<Edge> flavorTwoSeedEdges = collectFlavorTwoSeedEdges(graph);
        final List<Edge> subSeedEdges = collectSubSeedEdges(graph);
        final var result = new ArrayList<Edge>(flavorTwoSeedEdges.size() + subSeedEdges.size());
        result.addAll(flavorTwoSeedEdges);
        result.addAll(subSeedEdges);
        return result.stream();
    }

    private Stream<GraphDelta> derive(final Edge seed, final MapperGraph graph) {
        if (seed.getFrom().getLoc() instanceof SourceLocation && seed.getTo().getLoc() instanceof TargetLocation) {
            return deriveSeedEdge(seed, graph);
        }
        return deriveSubSeedEdge(seed, graph);
    }

    private Stream<GraphDelta> deriveSeedEdge(final Edge seedEdge, final MapperGraph graph) {
        final Node sourceSeed = seedEdge.getFrom();
        final Node targetSeed = seedEdge.getTo();

        final Node realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
        if (realisedSource == null) {
            return Stream.empty();
        }

        final Node realisedTarget = resolveRealisedCounterpart(targetSeed, graph);
        if (realisedTarget == null) {
            return Stream.empty();
        }

        final TypeMirror fromType = realisedSource.getType().orElse(null);
        final TypeMirror toType = realisedTarget.getType().orElse(null);

        if (fromType == null || toType == null) {
            return Stream.empty();
        }

        final Optional<AnnotationMirror> directive = seedEdge.getDirective();
        return bridges.stream().flatMap(bridge -> bridge.bridge(fromType, toType, resolveCtx)
                .map(step -> applyUnifiedEmissionRule(
                        realisedSource,
                        realisedTarget,
                        step,
                        directive,
                        bridge.getClass().getName())));
    }

    private Stream<GraphDelta> deriveSubSeedEdge(final Edge subSeedEdge, final MapperGraph graph) {
        final Node fromNode = subSeedEdge.getFrom();
        final Node toNode = subSeedEdge.getTo();

        final TypeMirror fromType = resolveNodeType(fromNode, graph);
        final TypeMirror toType = resolveNodeType(toNode, graph);

        if (fromType == null || toType == null) {
            return Stream.empty();
        }

        final Scope scope = fromNode.getScope();
        final io.github.joke.percolate.processor.graph.Location loc = fromNode.getLoc();

        final Optional<AnnotationMirror> directive = subSeedEdge.getDirective();
        return bridges.stream().flatMap(bridge -> bridge.bridge(fromType, toType, resolveCtx)
                .map(step -> applySubSeedEmissionRule(
                        fromNode,
                        toNode,
                        scope,
                        loc,
                        step,
                        directive,
                        bridge.getClass().getName())));
    }

    private GraphDelta applyUnifiedEmissionRule(
            final Node f,
            final Node t,
            final BridgeStep step,
            final Optional<AnnotationMirror> directive,
            final String strategyFqn) {
        final Scope scope = f.getScope();
        final io.github.joke.percolate.processor.graph.Location loc = f.getLoc();

        final Node inputNode = resolveOrCreateNode(scope, loc, step.getInputType(), f);
        final Node outputNode = resolveOrCreateNode(scope, loc, step.getOutputType(), t);

        final Edge realisedEdge = Edge.realised(
                inputNode, outputNode, step.getWeight(), Optional.empty(), step.getCodegen(), strategyFqn);

        final List<Node> nodes = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>(2);

        if (!inputNode.equals(f)) {
            nodes.add(inputNode);
        }
        if (!outputNode.equals(t)) {
            nodes.add(outputNode);
        }

        edges.add(realisedEdge);

        if (!inputNode.equals(f)) {
            final Edge subSeedEdge = Edge.subSeed(f, inputNode, strategyFqn, directive);
            edges.add(subSeedEdge);
        }

        return GraphDelta.of(nodes, edges);
    }

    private GraphDelta applySubSeedEmissionRule(
            final Node fromNode,
            final Node toNode,
            final Scope scope,
            final io.github.joke.percolate.processor.graph.Location loc,
            final BridgeStep step,
            final Optional<AnnotationMirror> directive,
            final String strategyFqn) {
        final TypeMirror fromType = fromNode.getType().orElse(null);
        final TypeMirror toType = toNode.getType().orElse(null);

        final Node inputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getInputType(),
                fromType != null && fromNode.getType().isPresent() ? fromNode : null);
        final Node outputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getOutputType(),
                toType != null && toNode.getType().isPresent() ? toNode : null);

        final Edge realisedEdge = Edge.realised(
                inputNode, outputNode, step.getWeight(), Optional.empty(), step.getCodegen(), strategyFqn);

        final List<Node> nodes = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>(2);

        if (!inputNode.equals(fromNode)) {
            nodes.add(inputNode);
        }
        if (!outputNode.equals(toNode)) {
            nodes.add(outputNode);
        }

        edges.add(realisedEdge);

        if (!inputNode.equals(fromNode)) {
            final Edge subSeedEdge = Edge.subSeed(fromNode, inputNode, strategyFqn, directive);
            edges.add(subSeedEdge);
        }

        return GraphDelta.of(nodes, edges);
    }

    private Node resolveOrCreateNode(
            final Scope scope,
            final io.github.joke.percolate.processor.graph.Location loc,
            @Nullable final TypeMirror type,
            @Nullable final Node existing) {
        if (type != null
                && existing != null
                && existing.getType().isPresent()
                && resolveCtx.types().isSameType(type, existing.getType().get())) {
            return existing;
        }
        return new Node(Optional.ofNullable(type), loc, scope, Optional.empty());
    }

    private List<Edge> collectFlavorTwoSeedEdges(final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getFrom().getLoc() instanceof SourceLocation)
                .filter(e -> e.getTo().getLoc() instanceof TargetLocation)
                .collect(toUnmodifiableList());
    }

    private List<Edge> collectSubSeedEdges(final MapperGraph graph) {
        return graph.edges().filter(e -> e.getKind() == EdgeKind.SUB_SEED).collect(toUnmodifiableList());
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
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.MARKER)
                .filter(e -> e.getFrom().equals(seedNode))
                .map(Edge::getTo)
                .filter(target -> target.getType().isPresent())
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private Node findTypedRealisedSource(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> e.getTo().equals(seedNode))
                .map(Edge::getFrom)
                .filter(source -> source.getType().isPresent())
                .findFirst()
                .orElse(null);
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
}
