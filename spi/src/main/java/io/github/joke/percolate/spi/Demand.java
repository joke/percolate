package io.github.joke.percolate.spi;

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
 * spec an assembly strategy gates on); and a nullness oracle ({@link #nullnessOf}) — the processor's nullability
 * resolution, injected so a strategy can type the ports and output it produces without graph access.
 *
 * <p>It deliberately exposes <b>no</b> snapshot of in-scope source values: the engine, not the strategy, sources
 * every input port (design D1). A strategy declares "what produces this target?" and the driver binds each
 * {@link OperationSpec} port to an in-scope source (or a fresh intermediate, or grounds a type-variable port by
 * matching); a strategy that needs a source element type declares a type-variable port instead of enumerating.
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

    /**
     * The binding/slot name this demand serves — the target field or port name the produced value is bound under.
     * A crossing strategy reads it to name the slot in its message (e.g. {@code requireNonNull}); empty when the
     * demand serves no named slot.
     */
    String bindingName();

    /** The nullness of {@code type} as declared at {@code scope} — the processor's nullability resolution. */
    Nullability nullnessOf(TypeMirror type, Element scope);
}
