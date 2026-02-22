package io.github.joke.percolate.stage;

public final class OptimizedGraphResult {

    private final GraphResult graphResult;

    public OptimizedGraphResult(GraphResult graphResult) {
        this.graphResult = graphResult;
    }

    public GraphResult graphResult() {
        return graphResult;
    }
}
