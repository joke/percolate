package io.github.joke.percolate.spi;

import lombok.Value;

/**
 * One port of a candidate operation as the engine bound it, handed to a {@link Constraint} (design D8 of change
 * {@code decouple-engine-from-strategy-semantics}): the declared {@link Port} plus an opaque identity for its
 * feeding value. {@link #getValueIdentity()} is equal (by {@code equals}) iff two ports were bound to the very same
 * graph value — enough for a constraint to test binding identity (e.g. "is this argument the enclosing method's own
 * parameter") without the constraint ever touching the graph itself.
 */
@Value
public class BoundPort {
    Port port;
    Object valueIdentity;
}
