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

    /** A concrete port whose {@link #type} fully determines the feeding value (no type variable). */
    public Port(final String name, final TypeMirror type, final Nullability nullness) {
        this(name, type, nullness, null);
    }
}
