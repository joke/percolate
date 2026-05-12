package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
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
        final var flavorTwoSeedEdges = BridgeGraphQuery.collectFlavorTwoSeedEdges(graph);
        final var subSeedEdges = BridgeGraphQuery.collectSubSeedEdges(graph);
        final var elementScopeSeedEdges = BridgeGraphQuery.collectElementScopeSeedEdges(graph);
        final var result =
                new ArrayList<Edge>(flavorTwoSeedEdges.size() + subSeedEdges.size() + elementScopeSeedEdges.size());
        result.addAll(flavorTwoSeedEdges);
        result.addAll(subSeedEdges);
        result.addAll(elementScopeSeedEdges);
        return result.stream();
    }

    private Stream<GraphDelta> derive(final Edge seed, final MapperGraph graph) {
        if (seed.getKind() == EdgeKind.SUB_SEED) {
            return deriveSubSeedEdge(seed, graph);
        }
        return deriveSeedEdge(seed, graph);
    }

    private Stream<GraphDelta> deriveSeedEdge(final Edge seedEdge, final MapperGraph graph) {
        final var sourceSeed = seedEdge.getFrom();
        final var targetSeed = seedEdge.getTo();

        final var realisedSource = BridgeGraphQuery.resolveRealisedCounterpart(sourceSeed, graph);
        if (realisedSource == null) {
            return Stream.empty();
        }

        final var realisedTarget = BridgeGraphQuery.resolveRealisedCounterpart(targetSeed, graph);
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

        final var fromType = BridgeGraphQuery.resolveNodeType(fromNode, graph);
        final var toType = BridgeGraphQuery.resolveNodeType(toNode, graph);

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
        final var isElement = loc instanceof ElementLocation;

        final var inputNode =
                resolveOrCreateNode(scope, loc, step.getInputType(), f, isElement ? f.getParent() : Optional.empty());
        final var outputNode =
                resolveOrCreateNode(scope, loc, step.getOutputType(), t, isElement ? t.getParent() : Optional.empty());

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

        if (!outputNode.equals(t)) {
            final var subSeedEdge = Edge.subSeed(outputNode, t, strategyFqn, directive);
            edges.add(subSeedEdge);
        }

        for (final var elementSeed : step.getElementSeeds()) {
            final var eFrom = new Node(
                    Optional.of(elementSeed.getInputType()),
                    new ElementLocation(elementSeed.getRole()),
                    scope,
                    Optional.of(inputNode));
            final var eTo = new Node(
                    Optional.of(elementSeed.getOutputType()),
                    new ElementLocation(elementSeed.getRole()),
                    scope,
                    Optional.of(outputNode));
            nodes.add(eFrom);
            nodes.add(eTo);
            final var seedEdge = Edge.elementSeed(eFrom, eTo, strategyFqn);
            edges.add(seedEdge);
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
        final var isElement = loc instanceof ElementLocation;

        final var inputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getInputType(),
                fromType != null ? fromNode : null,
                isElement ? fromNode.getParent() : Optional.empty());
        final var outputNode = resolveOrCreateNode(
                scope,
                loc,
                step.getOutputType(),
                toType != null ? toNode : null,
                isElement ? toNode.getParent() : Optional.empty());

        final var realisedEdge = Edge.realised(
                inputNode, outputNode, step.getWeight(), Optional.empty(), step.getCodegen(), strategyFqn);

        final List<Node> nodes = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>(2);

        if (!inputNode.equals(fromNode)) {
            nodes.add(inputNode);
            final var subSeedEdge = Edge.subSeed(fromNode, inputNode, strategyFqn, directive);
            edges.add(subSeedEdge);
        }
        if (!outputNode.equals(toNode)) {
            nodes.add(outputNode);
        }

        edges.add(realisedEdge);

        return GraphDelta.of(nodes, edges);
    }

    private Node resolveOrCreateNode(
            final Scope scope,
            final Location loc,
            @Nullable final TypeMirror type,
            @Nullable final Node existing,
            final Optional<Node> parent) {
        if (type != null
                && existing != null
                && existing.getType().isPresent()
                && resolveCtx.types().isSameType(type, existing.getType().get())) {
            return existing;
        }
        return new Node(Optional.ofNullable(type), loc, scope, parent);
    }
}
