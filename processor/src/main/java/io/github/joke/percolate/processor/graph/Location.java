package io.github.joke.percolate.processor.graph;

public interface Location {

    /**
     * Returns this location's contribution to the node id string.
     * For SourceLocation this encodes the access path segments;
     * for TargetLocation this encodes the target path segments;
     * for ElementLocation this returns "elem".
     */
    String segment();

    /** The slot name this location binds under: its last path segment, or the element role; empty when absent. */
    String slotName();

    /** Whether this location is a method's return root: the empty-path target location. */
    default boolean isReturnRoot() {
        return false;
    }
}
