package io.github.joke.percolate.processor.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

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
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            final boolean fromSource = edge.getFrom().getLoc() instanceof SourceLocation;
            final boolean toTarget = edge.getTo().getLoc() instanceof TargetLocation;
            if (!fromSource || !toTarget) {
                continue;
            }
            if (processedSeeds.contains(edge)) {
                continue;
            }
            processedSeeds.add(edge);

            final Node sourceSeed = edge.getFrom();
            final Node targetSeed = edge.getTo();

            final Node realisedSource = resolveRealisedCounterpart(sourceSeed, graph);
            final Node realisedTarget = resolveRealisedCounterpart(targetSeed, graph);

            if (realisedSource == null || realisedTarget == null) {
                final String message = "No realised path: could not find realised counterpart for "
                        + (realisedSource == null ? sourceSeed.id() : targetSeed.id());
                emitError(typeElement, edge, message);
                continue;
            }

            if (!hasRealisedPath(realisedSource, realisedTarget, graph)) {
                final String fromType =
                        realisedSource.getType().map(TypeMirror::toString).orElse("?");
                final String toType =
                        realisedTarget.getType().map(TypeMirror::toString).orElse("?");
                final String message = "No realised path between " + fromType + " and " + toType + " — gap at "
                        + realisedSource.id() + " → " + realisedTarget.id();
                emitError(typeElement, edge, message);
            }
        }
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
