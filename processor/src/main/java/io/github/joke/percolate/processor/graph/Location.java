package io.github.joke.percolate.processor.graph;

public interface Location {
    /**
     * Returns the full encoding of this location used for node identity (Node.id()).
     * This is a legacy method retained for backward compatibility; new code should
     * prefer {@link #segment()} which returns only this location's contribution to the id.
     */
    String encode();

    /**
     * Returns this location's contribution to the node id string.
     * For SourceLocation this encodes the access path segments;
     * for TargetLocation this encodes the target path segments;
     * for ElementLocation this returns "elem".
     */
    String segment();
}
