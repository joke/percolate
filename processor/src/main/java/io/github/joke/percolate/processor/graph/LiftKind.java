package io.github.joke.percolate.processor.graph;

/**
 * Discriminates the wrapping scope of a {@link LiftEdge}.
 *
 * <p>{@link #NULL_CHECK} is declared for future use by the {@code jspecify-nullability} change
 * and SHALL NOT be constructed in this refactor. {@link #OPTIONAL}, {@link #STREAM}, and
 * {@link #COLLECTION} replace the per-mapping {@code templateComposer} machinery from
 * the pre-refactor optional/container strategies.
 */
public enum LiftKind {
    NULL_CHECK,
    OPTIONAL,
    STREAM,
    COLLECTION
}
