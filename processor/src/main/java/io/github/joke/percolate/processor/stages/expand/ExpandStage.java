package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ExpandStage implements Stage {

    private final List<ExpansionPhase> phases;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        setCurrentMethodFromSeedEdges(collectSeedEdges(graph), ctx);

        for (final var phase : phases) {
            phase.apply(graph);
        }
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
        return graph.edges().filter(e -> e.getKind() == EdgeKind.SEED).collect(Collectors.toList());
    }
}
