package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.GroupOutcome;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class RealisationDiagnosticsStage implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final var unsatOutcomes = graph.groupOutcomes()
                .filter(outcome -> outcome.getKind() != GroupOutcome.Kind.SAT)
                .collect(Collectors.toUnmodifiableList());
        for (final var outcome : unsatOutcomes) {
            emitFor(graph, outcome, ctx);
        }
    }

    private void emitFor(final MapperGraph graph, final GroupOutcome outcome, final MapperContext ctx) {
        final var failingSlotOpt = outcome.getFailingSlot();
        if (failingSlotOpt.isEmpty()) {
            return;
        }
        final var failingSlot = failingSlotOpt.get();
        final var slotPath = renderSlotPath(failingSlot);
        final String message;
        switch (outcome.getKind()) {
            case UNSAT_NO_PLAN:
                message = formatNoPlan(graph, failingSlot, slotPath);
                break;
            case UNSAT_DID_NOT_CONVERGE:
                message = formatDidNotConverge(slotPath);
                break;
            default:
                return;
        }
        if (message.isEmpty()) {
            return;
        }
        diagnostics.error(ctx.getMapperType(), message);
    }

    private String formatNoPlan(final MapperGraph graph, final Node failingSlot, final String slotPath) {
        final var closest = walkClosestMiss(graph, failingSlot);
        final var closestLabel = renderSlotPath(closest);
        return String.format(
                "no plan for %s: %s has no producer in the graph. Likely missing: a @Map-annotated method whose source produces %s",
                slotPath, closestLabel, slotTypeName(closest));
    }

    private String formatDidNotConverge(final String slotPath) {
        return "no plan for " + slotPath
                + ": expansion did not converge within the per-slot round budget. Likely missing: a more direct conversion strategy";
    }

    private Node walkClosestMiss(final MapperGraph graph, final Node failingSlot) {
        final var realisedEdges =
                graph.edges().filter(e -> e.getKind() == EdgeKind.REALISED).collect(Collectors.toUnmodifiableList());
        final Set<Node> visited = new HashSet<>();
        final Deque<Node> queue = new ArrayDeque<>();
        queue.add(failingSlot);
        visited.add(failingSlot);
        Node deepest = failingSlot;
        while (!queue.isEmpty()) {
            final var current = queue.removeFirst();
            for (final var edge : realisedEdges) {
                if (!edge.getTo().equals(current)) {
                    continue;
                }
                final var prev = edge.getFrom();
                if (visited.add(prev)) {
                    deepest = prev;
                    queue.add(prev);
                }
            }
        }
        return deepest;
    }

    private String renderSlotPath(final Node node) {
        if (node.getLoc() instanceof TargetLocation) {
            final var segments = ((TargetLocation) node.getLoc()).getPath().getSegments();
            if (segments.isEmpty()) {
                return "tgt[]";
            }
            return "tgt[" + String.join(".", segments) + "]";
        }
        return node.id();
    }

    private String slotTypeName(final Node node) {
        return node.getType().map(TypeMirror::toString).orElse("?");
    }
}
