package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.LazyMappingGraph;

public final class ValidationResult {

    private final GraphResult graphResult;
    private final LazyMappingGraph lazyGraph;
    private final boolean hasFatalErrors;

    public ValidationResult(GraphResult graphResult, LazyMappingGraph lazyGraph, boolean hasFatalErrors) {
        this.graphResult = graphResult;
        this.lazyGraph = lazyGraph;
        this.hasFatalErrors = hasFatalErrors;
    }

    public GraphResult graphResult() {
        return graphResult;
    }

    public LazyMappingGraph lazyGraph() {
        return lazyGraph;
    }

    public boolean hasFatalErrors() {
        return hasFatalErrors;
    }
}
