package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * The myopic, local decision context handed to {@link ExpansionStrategy#expand}. It exposes only what a strategy
 * needs to make a <em>local</em> decision — the type to produce, the in-effect {@code @Map} {@link Directive}, and a
 * flat snapshot of in-scope source {@link Candidate}s — and deliberately exposes neither the graph nor any handle
 * from which a strategy could traverse it. The driver builds the candidate snapshot from the current group's view.
 */
public interface Frontier {

    /** The type the strategy is being asked to produce at this frontier. */
    TypeMirror targetType();

    /** The {@code @Map} configuration in effect for the binding being resolved, if any. */
    Optional<Directive> directive();

    /** A flat snapshot of the in-scope source values, scoped to the current group's view. */
    List<Candidate> candidates();
}
