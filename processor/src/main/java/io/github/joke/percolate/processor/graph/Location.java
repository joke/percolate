package io.github.joke.percolate.processor.graph;

public interface Location {

    /**
     * The resolution mode of the Value at this location (demand-driven-expansion D1): {@code FREE} for target
     * values and conversion intermediates (the full strategy set may produce them), {@code ACCESS} for
     * multi-segment source-path values (only accessor strategies may, re-demanding the parent path), {@code LEAF}
     * for single-segment source-path parameter roots and container element roots (base cases — no expansion),
     * and {@code CONSTANT} for literal origins. The work-list dispatch and the cost base-case rule key off this
     * mode, not {@code instanceof} on the concrete Location types.
     */
    enum Role {
        FREE,
        ACCESS,
        LEAF,
        CONSTANT
    }

    /** This location's resolution mode. */
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
