package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableList;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidatePathsPhase implements ValidationPhase {

    private final Diagnostics diagnostics;

    @Override
    public void apply(final @Nullable MapperGraph graph, final TypeElement typeElement) {
        if (graph == null) {
            return;
        }
        final Set<Edge> processedSeeds = new HashSet<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (isUnprocessedSeedBetweenSourceAndTarget(edge, processedSeeds)) {
                validateSeedEdge(edge, graph, typeElement);
            }
        }
    }

    private boolean isUnprocessedSeedBetweenSourceAndTarget(final Edge edge, final Set<Edge> processedSeeds) {
        return edge.getKind() == EdgeKind.SEED
                && edge.getFrom().getLoc() instanceof SourceLocation
                && edge.getTo().getLoc() instanceof TargetLocation
                && processedSeeds.add(edge);
    }

    private void validateSeedEdge(final Edge edge, final MapperGraph graph, final TypeElement typeElement) {
        final Node sourceSeed = edge.getFrom();
        final Node targetSeed = edge.getTo();

        final Node realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
        final Node realisedTarget = resolveRealisedCounterpart(targetSeed, graph);

        if (realisedSource == null || realisedTarget == null) {
            final String message = "No realised path: could not find realised counterpart for "
                    + (realisedSource == null ? sourceSeed.id() : targetSeed.id());
            emitError(typeElement, edge, message);
            return;
        }

        if (!hasRealisedPath(realisedSource, realisedTarget, graph)) {
            emitError(typeElement, edge, buildNoPathMessage(realisedSource, realisedTarget));
        }
    }

    private String buildNoPathMessage(final Node from, final Node to) {
        final String fromType = from.getType().map(TypeMirror::toString).orElse("?");
        final String toType = to.getType().map(TypeMirror::toString).orElse("?");
        return "No realised path between " + fromType + " and " + toType + " — gap at " + from.id() + " → " + to.id();
    }

    private boolean hasRealisedPath(final Node from, final Node to, final MapperGraph graph) {
        final Set<Node> visited = new HashSet<>();
        final Deque<Node> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            final Node current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(to)) {
                return true;
            }
            for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
                if (edge.getKind() == EdgeKind.REALISED && edge.getFrom().equals(current)) {
                    queue.add(edge.getTo());
                }
            }
        }
        return false;
    }

    private void emitError(final TypeElement typeElement, final Edge seedEdge, final String message) {
        if (seedEdge.getDirective().isPresent()) {
            diagnostics.error(typeElement, seedEdge.getDirective().get(), null, message);
        } else {
            diagnostics.error(typeElement, message);
        }
    }

    private @Nullable Node resolveRealisedCounterpart(final Node seedNode, final MapperGraph graph) {
        if (seedNode.getType().isPresent()) {
            return seedNode;
        }
        final var fromMarker = findTypedMarkerTarget(seedNode, graph);
        if (fromMarker != null) {
            return fromMarker;
        }
        return findTypedRealisedSource(seedNode, graph);
    }

    @Nullable
    private Node findTypedMarkerTarget(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.MARKER)
                .filter(e -> e.getFrom().equals(seedNode))
                .filter(e -> e.getTo().getType().isPresent())
                .findFirst()
                .map(Edge::getTo)
                .orElse(null);
    }

    @Nullable
    private Node findTypedRealisedSource(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> e.getTo().equals(seedNode))
                .filter(e -> e.getFrom().getType().isPresent())
                .findFirst()
                .map(Edge::getFrom)
                .orElse(null);
    }
}
