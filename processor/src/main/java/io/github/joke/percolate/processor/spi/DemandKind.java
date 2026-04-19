package io.github.joke.percolate.processor.spi;

/**
 * Classifies an {@link ExpansionDemand} so that a {@link ValueExpansionStrategy} can decline
 * demands outside its wheelhouse cheaply (returning {@code Optional.empty()}).
 */
public enum DemandKind {

    /** Demand for an incoming {@code PropertyReadEdge} on a property node. */
    PROPERTY_READ,

    /** Demand for an incoming {@code TypeTransformEdge} on a typed value or target slot. */
    TYPE_TRANSFORM,

    /** Demand to populate a {@code TargetSlotNode} from an upstream expression. */
    TARGET_SLOT,

    /** Demand to construct a {@code TargetRootNode}'s slot set. */
    ROOT_CONSTRUCTION
}
