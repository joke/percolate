package io.github.joke.percolate.spi;

import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * The produce shape of a {@link Demand}, handed to {@link ExpansionStrategy#expand}: a producer answers "what produces
 * this demanded target?". It carries the demanded type and {@link Nullability}; the in-effect {@code @Map}
 * {@link Directive} (its {@code defaultValue}/{@code constant}, travelling with the demand, never stamped on a Value);
 * the declared-children name set at the current target level (the goal spec an assembly strategy gates on); and the
 * binding/slot name the demand serves. The output type is the demand; the strategy declares its input ports.
 *
 * <p>It exposes <b>no</b> candidate snapshot of in-scope source Values: a producer declares "what produces this
 * target?" and the driver binds each {@link OperationSpec} port to an in-scope source (or a fresh intermediate, or
 * grounds a type-variable port by matching); a strategy that needs a source element type declares a type-variable port
 * instead of enumerating.
 */
public interface ProduceDemand extends Demand {

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
}
