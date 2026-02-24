package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.LazyMappingGraph;

public final class OptimizedGraphResult {

    private final GraphResult graphResult;
    private final LazyMappingGraph lazyGraph;

    public OptimizedGraphResult(GraphResult graphResult, LazyMappingGraph lazyGraph) {
        this.graphResult = graphResult;
        this.lazyGraph = lazyGraph;
    }

    public GraphResult graphResult() {
        return graphResult;
    }

    public LazyMappingGraph lazyGraph() {
        return lazyGraph;
    }
}
