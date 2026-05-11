package io.github.joke.percolate.processor.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateMarkersPhase implements ValidationPhase {

    private final Diagnostics diagnostics;

    @Override
    public void apply(final @org.jspecify.annotations.Nullable MapperGraph graph, final TypeElement typeElement) {
        if (graph == null) {
            return;
        }
        final Set<Node> checkedNodes = new HashSet<>();

        for (final var edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() != EdgeKind.SEED) {
                continue;
            }
            checkNodeForMarkers(edge.getFrom(), edge, graph, typeElement, checkedNodes);
            checkNodeForMarkers(edge.getTo(), edge, graph, typeElement, checkedNodes);
        }
    }

    private void checkNodeForMarkers(
            final Node node,
            final Edge seedEdge,
            final MapperGraph graph,
            final TypeElement typeElement,
            final Set<Node> checkedNodes) {
        if (checkedNodes.contains(node)) {
            return;
        }
        checkedNodes.add(node);

        if (node.getType().isPresent()) {
            return;
        }

        final var markerCount = countOutgoingMarkers(node, graph);

        if (markerCount == 0) {
            final var message = "No strategy could realise " + node.id() + " — no MARKER edges emitted";
            if (seedEdge.getDirective().isPresent()) {
                diagnostics.error(typeElement, seedEdge.getDirective().get(), null, message);
            } else {
                diagnostics.error(typeElement, message);
            }
        }
    }

    private int countOutgoingMarkers(final Node node, final MapperGraph graph) {
        var count = 0;
        for (final var edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() == EdgeKind.MARKER && edge.getFrom().equals(node)) {
                count++;
            }
        }
        return count;
    }
}
