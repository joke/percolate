package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One input of an operation's ordered port signature: the consumer contract a feeding value must satisfy —
 * the port's name, its declared type, and its declared nullness. The port signature lives on the consumer
 * (the operation), never on an edge or a grouping label.
 *
 * <p>A port is <b>concrete</b> by default (use the three-argument constructor): its {@link #type} fully determines
 * the feeding value. A <b>type-variable</b> port additionally carries a {@link PortType} {@link #template} (e.g.
 * {@code F<A>}); the engine sources it by grounding-by-match (design D2) — unifying the template against an in-scope
 * concrete source and instantiating one concrete port per match. For a template port {@link #type} holds only a
 * representative shape (the template's erasure) and is never used to source the port; grounding replaces it.
 *
 * <p>A port is <b>reuse-only</b> ({@link #reuseOnly}) when its feeding value must already exist in scope (a parameter
 * or a materialised source): the driver binds an in-scope source if one matches, otherwise the operation simply does
 * not apply — it is never satisfied by minting a fresh intermediate. This is how a <em>consuming</em> operation whose
 * input is structurally larger than its output (e.g. {@code unwrap}: {@code T ← Optional<T>}) avoids manufacturing an
 * ever-deeper wrapper to feed itself; you never wrap a value just to unwrap it.
 */
@Value
@AllArgsConstructor
public class Port {
    String name;
    TypeMirror type;
    Nullability nullness;

    /** The variable-carrying shape of this port, or {@code null} for an ordinary concrete port. */
    @Nullable
    PortType template;

    /** Whether this port must bind an already-in-scope source and is never satisfied by a fresh intermediate. */
    boolean reuseOnly;

    /** A concrete port whose {@link #type} fully determines the feeding value (no type variable). */
    public Port(final String name, final TypeMirror type, final Nullability nullness) {
        this(name, type, nullness, null, false);
    }

    /** A type-variable port carrying a {@link PortType} {@code template} the engine grounds by match. */
    public Port(
            final String name, final TypeMirror type, final Nullability nullness, final @Nullable PortType template) {
        this(name, type, nullness, template, false);
    }

    /** A concrete, <b>reuse-only</b> port: bound to an in-scope source or the operation does not apply (never minted). */
    public static Port reuse(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, true);
    }
}
