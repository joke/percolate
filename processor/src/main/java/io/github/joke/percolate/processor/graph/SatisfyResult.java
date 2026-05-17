package io.github.joke.percolate.processor.graph;

import org.jspecify.annotations.Nullable;

public final class SatisfyResult {
    private SatisfyResult() {
        throw new UnsupportedOperationException();
    }

    public static SatisfyResult sat() {
        return new SatisfyResult(SatisfyOutcome.SAT, "", null, 0, null, null, null, null, null);
    }

    public static SatisfyResult unsat(final String message, final @Nullable String strategyFqn, final int depth) {
        return new SatisfyResult(SatisfyOutcome.UNSAT, message, strategyFqn, depth, null, null, null, null, null);
    }

    public static SatisfyResult unsat(
            final String message,
            final @Nullable String strategyFqn,
            final int depth,
            final @Nullable String edgeInputType,
            final @Nullable String edgeOutputType,
            final @Nullable String promiseKind,
            final @Nullable String promiseInputType,
            final @Nullable String promiseOutputType) {
        return new SatisfyResult(
                SatisfyOutcome.UNSAT,
                message,
                strategyFqn,
                depth,
                edgeInputType,
                edgeOutputType,
                promiseKind,
                promiseInputType,
                promiseOutputType);
    }

    private final SatisfyOutcome outcome;
    private final String message;
    private final @Nullable String strategyFqn;
    private final int depth;
    private final @Nullable String edgeInputType;
    private final @Nullable String edgeOutputType;
    private final @Nullable String promiseKind;
    private final @Nullable String promiseInputType;
    private final @Nullable String promiseOutputType;

    SatisfyResult(
            final SatisfyOutcome outcome,
            final String message,
            final @Nullable String strategyFqn,
            final int depth,
            final @Nullable String edgeInputType,
            final @Nullable String edgeOutputType,
            final @Nullable String promiseKind,
            final @Nullable String promiseInputType,
            final @Nullable String promiseOutputType) {
        this.outcome = outcome;
        this.message = message;
        this.strategyFqn = strategyFqn;
        this.depth = depth;
        this.edgeInputType = edgeInputType;
        this.edgeOutputType = edgeOutputType;
        this.promiseKind = promiseKind;
        this.promiseInputType = promiseInputType;
        this.promiseOutputType = promiseOutputType;
    }

    public boolean isSat() {
        return outcome == SatisfyOutcome.SAT;
    }

    public boolean isUnsat() {
        return outcome == SatisfyOutcome.UNSAT;
    }

    public String message() {
        return message;
    }

    public @Nullable String strategyFqn() {
        return strategyFqn;
    }

    public int depth() {
        return depth;
    }

    public SatisfyOutcome outcome() {
        return outcome;
    }

    public @Nullable String edgeInputType() {
        return edgeInputType;
    }

    public @Nullable String edgeOutputType() {
        return edgeOutputType;
    }

    public @Nullable String promiseKind() {
        return promiseKind;
    }

    public @Nullable String promiseInputType() {
        return promiseInputType;
    }

    public @Nullable String promiseOutputType() {
        return promiseOutputType;
    }
}
