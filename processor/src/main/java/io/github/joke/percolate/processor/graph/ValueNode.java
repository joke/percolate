package io.github.joke.percolate.processor.graph;

import javax.lang.model.type.TypeMirror;

/**
 * Typed vertex in a per-method {@link ValueGraph}.
 *
 * <p>Subtypes are restricted to this package via a package-private constructor, approximating
 * Java 17 {@code sealed} semantics for Java 11. The four permitted subtypes are:
 * {@link SourceParamNode}, {@link PropertyNode}, {@link TypedValueNode}, {@link TargetSlotNode}.
 */
public abstract class ValueNode {

    /** Package-private — prevents subclassing from outside this package. */
    ValueNode() {}

    /** The type carried by this node; used by strategies to propose transform edges. */
    public abstract TypeMirror getType();
}
