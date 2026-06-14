package io.github.joke.percolate.processor.graph;

public interface Location {

    /**
     * The planning role of the Value at this location (design D5): {@code SUPPLY} for parameter / source-path
     * values, {@code DEMAND} for target values, {@code ELEMENT} for container child-scope element roots, and
     * {@code CONSTANT} for literal origins. Replaces {@code instanceof} dispatch on the concrete Location types.
     */
    enum Role {
        SUPPLY,
        DEMAND,
        ELEMENT,
        CONSTANT
    }

    /** This location's planning role. */
    Role role();

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
