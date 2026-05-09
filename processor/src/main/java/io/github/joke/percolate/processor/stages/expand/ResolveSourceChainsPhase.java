package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.GraphDelta;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.spi.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ResolveSourceChainsPhase implements ExpansionPhase {

    private final List<SourceStep> sourceSteps;
    private final ResolveCtx resolveCtx;

    @Override
    public void apply(final MapperGraph graph) {
        final List<Edge> sourceSeedEdges = collectSourceSeedEdges(graph);
        final List<Edge> untypedSourceEdges = collectUntypedSourceEdges(graph);

        final var allEdges = new ArrayList<Edge>(sourceSeedEdges.size() + untypedSourceEdges.size());
        allEdges.addAll(sourceSeedEdges);
        allEdges.addAll(untypedSourceEdges);

        allEdges.stream().distinct().flatMap(seed -> derive(seed, graph)).forEach(graph::apply);
    }

    private Stream<GraphDelta> derive(final Edge seed, final MapperGraph graph) {
        final Node sourceNode = seed.getFrom();
        final Node targetNode = seed.getTo();

        if (!(sourceNode.getLoc() instanceof SourceLocation)) {
            return Stream.empty();
        }

        if (!(targetNode.getLoc() instanceof SourceLocation)) {
            return Stream.empty();
        }

        final TypeMirror sourceType = findSourceType(sourceNode, graph);
        if (sourceType == null) {
            return Stream.empty();
        }

        final SourceLocation targetLoc = (SourceLocation) targetNode.getLoc();
        final String pathTail = pathTail(targetLoc);

        return sourceSteps.stream().flatMap(strategy -> strategy.stepsFrom(sourceType, pathTail, resolveCtx)
                .map(step -> {
                    final Node realisedNode = allocateRealisedNode(sourceNode, targetLoc, step);
                    final Edge realisedEdge = Edge.realised(
                            sourceNode,
                            realisedNode,
                            step.getWeight(),
                            Optional.empty(),
                            step.getCodegen(),
                            strategy.getClass().getName());

                    final Edge markerEdge = Edge.marker(
                            targetNode, realisedNode, strategy.getClass().getName());

                    final List<Node> nodes = new ArrayList<>(1);
                    nodes.add(realisedNode);

                    final List<Edge> edges = new ArrayList<>(2);
                    edges.add(realisedEdge);
                    edges.add(markerEdge);

                    return GraphDelta.of(nodes, edges);
                }));
    }

    private String pathTail(final SourceLocation sourceLoc) {
        final List<String> segments = sourceLoc.getPath().getSegments();
        if (segments.isEmpty()) {
            return "";
        }
        return segments.get(segments.size() - 1);
    }

    private List<Edge> collectSourceSeedEdges(final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getFrom().getLoc() instanceof SourceLocation)
                .collect(toUnmodifiableList());
    }

    private List<Edge> collectUntypedSourceEdges(final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getFrom().getLoc() instanceof SourceLocation)
                .filter(e -> !e.getFrom().getType().isPresent())
                .filter(e -> e.getTo().getLoc() instanceof SourceLocation)
                .collect(toUnmodifiableList());
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
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.MARKER)
                .filter(e -> e.getFrom().equals(node))
                .map(Edge::getTo)
                .filter(target -> target.getType().isPresent())
                .map(target -> target.getType().get())
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private TypeMirror findTypeViaRealised(final Node node, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> e.getTo().equals(node))
                .map(Edge::getFrom)
                .filter(source -> source.getType().isPresent())
                .map(source -> source.getType().get())
                .findFirst()
                .orElse(null);
    }

    private Node allocateRealisedNode(final Node seedNode, final SourceLocation sourceLoc, final Step step) {
        final TypeMirror realisedType = step.getProduces();
        return new Node(Optional.of(realisedType), sourceLoc, seedNode.getScope(), Optional.empty());
    }
}
