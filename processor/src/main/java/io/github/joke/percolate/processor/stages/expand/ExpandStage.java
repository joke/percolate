package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
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
        setCurrentMethodFromSeedEdges(seedEdges, ctx);

        int totalAdditions = 0;
        boolean changed;
        do {
            changed = false;
            for (final ExpansionPhase phase : phases) {
                final boolean phaseChanged = phase.apply(graph);
                if (phaseChanged) {
                    changed = true;
                    totalAdditions++;
                }

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

                if (totalAdditions > EXPANSION_BUDGET) {
                    final Edge originatingSeed = findSeedEdge(seedEdges);
                    final String message = "Expansion budget exceeded (" + totalAdditions + " > " + EXPANSION_BUDGET
                            + ")";
                    if (originatingSeed != null
                            && originatingSeed.getDirective().isPresent()) {
                        diagnostics.error(
                                ctx.getMapperType(), originatingSeed.getDirective().get(), null, message);
                    } else {
                        diagnostics.error(ctx.getMapperType(), message);
                    }
                    return;
                }
            }
        } while (changed);
    }

    private void setCurrentMethodFromSeedEdges(final List<Edge> seedEdges, final MapperContext ctx) {
        for (final Edge seedEdge : seedEdges) {
            final var scope = seedEdge.getFrom().getScope();
            if (scope instanceof MethodScope) {
                final MethodScope methodScope = (MethodScope) scope;
                ctx.setCurrentMethod(methodScope.getMethod());
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
}
