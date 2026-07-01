package io.github.joke.percolate.processor.test.fixtures;

import org.jspecify.annotations.NullMarked;

/**
 * A fixture with methods of varied arity and return type, so {@code DiscoverCallableMethodsStage} can be unit-tested
 * at its seam: only single-parameter, non-{@code Object} methods are indexed, keyed by return type. An abstract class
 * (not an interface) so it genuinely inherits {@code Object}'s single-parameter {@code equals(Object)} — the case the
 * Object-method filter must drop.
 */
@NullMarked
public abstract class CallableFixtures {

    public abstract Human makeHuman(Person p);

    public abstract String describe(Person p);

    public abstract Human noArg();

    public abstract Human pair(Person a, Person b);
}
