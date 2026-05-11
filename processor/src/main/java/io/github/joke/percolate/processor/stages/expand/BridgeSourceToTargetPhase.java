package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.Location;
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
        final var flavorTwoSeedEdges = collectFlavorTwoSeedEdges(graph);
        final var subSeedEdges = collectSubSeedEdges(graph);
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
        final var sourceSeed = seedEdge.getFrom();
        final var targetSeed = seedEdge.getTo();

        final var realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
        if (realisedSource == null) {
            return Stream.empty();
        }

        final var realisedTarget = resolveRealisedCounterpart(targetSeed, graph);
        if (realisedTarget == null) {
            return Stream.empty();
        }

        final var fromType = realisedSource.getType().orElse(null);
        final var toType = realisedTarget.getType().orElse(null);

        if (fromType == null || toType == null) {
            return Stream.empty();
        }

        final var directive = seedEdge.getDirective();
        return bridges.stream().flatMap(bridge -> bridge.bridge(fromType, toType, resolveCtx)
                .map(step -> applyUnifiedEmissionRule(
                        realisedSource,
                        realisedTarget,
                        step,
                        directive,
                        bridge.getClass().getName())));
    }

    private Stream<GraphDelta> deriveSubSeedEdge(final Edge subSeedEdge, final MapperGraph graph) {
        final var fromNode = subSeedEdge.getFrom();
        final var toNode = subSeedEdge.getTo();

        final var fromType = resolveNodeType(fromNode, graph);
        final var toType = resolveNodeType(toNode, graph);

        if (fromType == null || toType == null) {
            return Stream.empty();
        }

        final var scope = fromNode.getScope();
        final var loc = fromNode.getLoc();

        final var directive = subSeedEdge.getDirective();
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
        final var scope = f.getScope();
        final var loc = f.getLoc();

        final var inputNode = resolveOrCreateNode(scope, loc, step.getInputType(), f);
        final var outputNode = resolveOrCreateNode(scope, loc, step.getOutputType(), t);

        final var realisedEdge = Edge.realised(
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
            final var subSeedEdge = Edge.subSeed(f, inputNode, strategyFqn, directive);
            edges.add(subSeedEdge);
        }

        return GraphDelta.of(nodes, edges);
    }

    private GraphDelta applySubSeedEmissionRule(
            final Node fromNode,
            final Node toNode,
            final Scope scope,
            final Location loc,
            final BridgeStep step,
            final Optional<AnnotationMirror> directive,
            final String strategyFqn) {
        final var fromType = fromNode.getType().orElse(null);
        final var toType = toNode.getType().orElse(null);

        final var inputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getInputType(),
                fromType != null && fromNode.getType().isPresent() ? fromNode : null);
        final var outputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getOutputType(),
                toType != null && toNode.getType().isPresent() ? toNode : null);

        final var realisedEdge = Edge.realised(
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
            final var subSeedEdge = Edge.subSeed(fromNode, inputNode, strategyFqn, directive);
            edges.add(subSeedEdge);
        }

        return GraphDelta.of(nodes, edges);
    }

    private Node resolveOrCreateNode(
            final Scope scope, final Location loc, @Nullable final TypeMirror type, @Nullable final Node existing) {
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

        final var markerTarget = findTypedMarkerTarget(seedNode, graph);
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
        final var markerTarget = findTypedMarkerTarget(node, graph);
        if (markerTarget != null && markerTarget.getType().isPresent()) {
            return markerTarget.getType().get();
        }
        final var realisedSource = findTypedRealisedSource(node, graph);
        if (realisedSource != null && realisedSource.getType().isPresent()) {
            return realisedSource.getType().get();
        }
        return null;
    }
}
