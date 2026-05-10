package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static java.util.stream.Collectors.toUnmodifiableList;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ExpandStage implements Stage {

    static final int MAX_EXPANSION_ROUNDS = 64;

    private final List<ExpansionPhase> phases;
    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final var seedEdges = collectSeedEdges(graph);
        setCurrentMethodFromSeedEdges(seedEdges, ctx);

        var round = 0;
        while (round <= MAX_EXPANSION_ROUNDS) {
            final var before = graph.edgeCount();

            for (final var phase : phases) {
                phase.apply(graph);

                if (graph.hasSeedSubSeedCycles()) {
                    diagnostics.error(
                            ctx.getMapperType(), "Cycle detected in expansion — sub-directive lineage loops back");
                    return;
                }
            }

            round++;

            if (graph.edgeCount() == before) {
                return;
            }
        }

        diagnostics.error(ctx.getMapperType(), "Expansion did not converge after " + round + " rounds");
    }

    private void setCurrentMethodFromSeedEdges(final List<Edge> seedEdges, final MapperContext ctx) {
        seedEdges.stream()
                .map(Edge::getFrom)
                .map(Node::getScope)
                .filter(s -> s instanceof MethodScope)
                .map(s -> (MethodScope) s)
                .findFirst()
                .ifPresent(methodScope -> ctx.setCurrentMethod(methodScope.getMethod()));
    }

    private List<Edge> collectSeedEdges(final MapperGraph graph) {
        return graph.edges().filter(e -> e.getKind() == EdgeKind.SEED).collect(toUnmodifiableList());
    }
}
