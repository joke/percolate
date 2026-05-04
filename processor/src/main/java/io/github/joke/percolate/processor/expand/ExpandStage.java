package io.github.joke.percolate.processor.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.Stage;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ExpandStage implements Stage {

    private static final int EXPANSION_BUDGET = 100;

    private final List<ExpansionPhase> phases;
    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final List<Edge> seedEdges = collectSeedEdges(graph);

        for (final ExpansionPhase phase : phases) {
            phase.apply(graph);

            if (graph.hasSeedSubSeedCycles()) {
                final Edge cyclingSeed = findSeedEdge(seedEdges);
                if (cyclingSeed != null && cyclingSeed.getDirective().isPresent()) {
                    diagnostics.error(
                            ctx.getMapperType(),
                            cyclingSeed.getDirective().get(),
                            null,
                            "Cycle detected in expansion — sub-directive lineage loops back");
                } else {
                    diagnostics.error(
                            ctx.getMapperType(), "Cycle detected in expansion — sub-directive lineage loops back");
                }
                return;
            }

            if (checkBudget(graph, seedEdges, ctx.getMapperType())) {
                return;
            }
        }
    }

    private List<Edge> collectSeedEdges(final MapperGraph graph) {
        final List<Edge> result = new ArrayList<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() == EdgeKind.SEED) {
                result.add(edge);
            }
        }
        return result;
    }

    private @Nullable Edge findSeedEdge(final List<Edge> seedEdges) {
        return seedEdges.isEmpty() ? null : seedEdges.get(0);
    }

    private boolean checkBudget(
            final MapperGraph graph,
            final List<Edge> seedEdges,
            final javax.lang.model.element.TypeElement mapperType) {
        final Map<String, Integer> expansionCount = new HashMap<>();
        for (final Edge edge : graph.edges().collect(toUnmodifiableList())) {
            if (edge.getKind() == EdgeKind.SUB_SEED) {
                final String fromId = edge.getFrom().id();
                final int count = expansionCount.merge(fromId, 1, Integer::sum);
                if (count > EXPANSION_BUDGET) {
                    final String message = "Expansion budget exceeded for seed " + fromId + " (" + count + " > "
                            + EXPANSION_BUDGET + ")";
                    final Edge originatingSeed = findSeedEdge(seedEdges);
                    if (originatingSeed != null
                            && originatingSeed.getDirective().isPresent()) {
                        diagnostics.error(
                                mapperType, originatingSeed.getDirective().get(), null, message);
                    } else {
                        diagnostics.error(mapperType, message);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
