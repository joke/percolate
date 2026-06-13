package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * The myopic, local decision context handed to {@link ExpansionStrategy#expand} for one unsatisfied {@code Value}
 * demand. It exposes only what a strategy needs to make a <em>local</em> decision and deliberately exposes neither
 * the graph nor any handle from which a strategy could traverse it. It replaces the former {@code Frontier}.
 *
 * <p>It carries the demanded type and {@link Nullability}; the in-effect {@code @Map} {@link Directive} (travelling
 * with the demand, never stamped on a Value); the declared-children name set at the current target level (the goal
 * spec an assembly strategy gates on); a flat snapshot of in-scope source {@link Candidate}s; and a nullness oracle
 * ({@link #nullnessOf}) — the processor's nullability resolution, injected so a strategy can type the ports and
 * output it produces without graph access.
 */
public interface Demand {

    /** The type the strategy is being asked to produce. */
    TypeMirror targetType();

    /** The nullness the strategy is being asked to produce — part of the demanded Value's identity. */
    Nullability targetNullness();

    /** The {@code @Map} configuration in effect for the binding being resolved, if any. */
    Optional<Directive> directive();

    /**
     * The declared-children name set at the current target level (e.g. {@code {number, street}} for a target whose
     * dotted paths declare {@code number} and {@code street}). Assembly strategies gate on it; empty otherwise.
     */
    Set<String> declaredChildren();

    /** A flat snapshot of the in-scope source values, scope-confined to the current (method or child) scope. */
    List<Candidate> candidates();

    /** The nullness of {@code type} as declared at {@code scope} — the processor's nullability resolution. */
    Nullability nullnessOf(TypeMirror type, Element scope);
}
