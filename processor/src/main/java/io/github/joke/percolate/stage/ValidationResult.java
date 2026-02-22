package io.github.joke.percolate.stage;

public final class ValidationResult {

    private final GraphResult graphResult;
    private final boolean hasFatalErrors;

    public ValidationResult(GraphResult graphResult, boolean hasFatalErrors) {
        this.graphResult = graphResult;
        this.hasFatalErrors = hasFatalErrors;
    }

    public GraphResult graphResult() {
        return graphResult;
    }

    public boolean hasFatalErrors() {
        return hasFatalErrors;
    }
}
