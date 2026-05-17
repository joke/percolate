package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SatisfyOutcome;
import io.github.joke.percolate.processor.graph.SatisfyResult;
import io.github.joke.percolate.processor.graph.SatisfySearch;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateRealisationStage implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        if (ctx.isScarred(diagnostics)) {
            return;
        }

        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }

        final var satisfySearch = new SatisfySearch(graph);
        final var mapperType = ctx.getMapperType();
        if (mapperType == null) {
            return;
        }

        for (final var edge : graph.edges().collect(java.util.stream.Collectors.toList())) {
            if (edge.getKind() == EdgeKind.SEED
                    && edge.getFrom().getLoc() instanceof SourceLocation
                    && edge.getTo().getLoc() instanceof TargetLocation) {
                final var targetNode = resolveRealisedTarget(edge.getTo(), graph);
                if (targetNode != null) {
                    final var result = satisfySearch.satisfy(targetNode, new HashSet<>());
                    if (result.outcome() == SatisfyOutcome.UNSAT) {
                        final var mirror = edge.getDirective().orElse(null);
                        final var message = formatDiagnostic(targetNode, result);
                        diagnostics.error(mapperType, mirror, null, message);
                    }
                }
            }
        }
    }

    private String formatDiagnostic(final Node targetNode, final SatisfyResult result) {
        final var targetLoc = targetNode.getLoc();
        final var path = targetLoc instanceof TargetLocation
                ? ((TargetLocation) targetLoc).getPath().toString()
                : targetLoc.encode();
        final var methodName = targetNode.getScope().encode();

        if (result.message().contains("no producer for") && !result.message().contains("cycle detected")) {
            final var targetType = targetNode.getType().map(Object::toString).orElse("?");
            return String.format(
                    "no plan for tgt[%s] in method %s%n"
                            + "  the target type %s has no producer in the graph.%n"
                            + "  Likely missing: a strategy that produces %s from the available source parameters.",
                    path, methodName, targetType, targetType);
        }

        if (result.message().contains("cycle detected")) {
            final var cycleNode = result.message().substring("cycle detected at ".length());
            return String.format(
                    "no plan for tgt[%s] in method %s%n"
                            + "  cycle detected at %s.%n"
                            + "  Likely missing: a mapping that breaks the cycle between the involved types.",
                    path, methodName, cycleNode);
        }

        final var strategyShort = result.strategyFqn() != null
                ? result.strategyFqn().substring(result.strategyFqn().lastIndexOf('.') + 1)
                : "unknown";

        final var edgeInput = result.edgeInputType() != null ? result.edgeInputType() : "?";
        final var edgeOutput = result.edgeOutputType() != null ? result.edgeOutputType() : "?";

        if (result.promiseKind() != null && result.promiseInputType() != null && result.promiseOutputType() != null) {
            return String.format(
                    "no plan for tgt[%s] in method %s%n"
                            + "  considered %s's REALISED %s → %s,%n"
                            + "  but its %s %s → %s was not producible.%n"
                            + "  Likely missing: a @Map-annotated method producing %s from %s.",
                    path,
                    methodName,
                    strategyShort,
                    edgeInput,
                    edgeOutput,
                    result.promiseKind(),
                    result.promiseInputType(),
                    result.promiseOutputType(),
                    result.promiseOutputType(),
                    result.promiseInputType());
        }

        return result.message();
    }

    private @Nullable Node resolveRealisedTarget(final Node targetSeed, final MapperGraph graph) {
        if (targetSeed.getType().isPresent()) {
            return targetSeed;
        }
        final var markerTarget = findTypedMarkerTarget(targetSeed, graph);
        if (markerTarget != null) {
            return markerTarget;
        }
        return findTypedRealisedSource(targetSeed, graph);
    }

    private @Nullable Node findTypedMarkerTarget(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.MARKER)
                .filter(e -> e.getFrom().equals(seedNode))
                .filter(e -> e.getTo().getType().isPresent())
                .findFirst()
                .map(Edge::getTo)
                .orElse(null);
    }

    private @Nullable Node findTypedRealisedSource(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> e.getTo().equals(seedNode))
                .filter(e -> e.getFrom().getType().isPresent())
                .findFirst()
                .map(Edge::getFrom)
                .orElse(null);
    }
}
