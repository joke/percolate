package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One input of an operation's ordered port signature: the consumer contract a feeding value must satisfy —
 * the port's name, its declared type, its declared nullness, and its {@link Sourcing} mode. The port signature
 * lives on the consumer (the operation), never on an edge or a grouping label.
 *
 * <p>A port is <b>concrete</b> by default (use the three-argument constructor): its {@link #type} fully determines
 * the feeding value. A <b>type-variable</b> port additionally carries a {@link PortType} {@link #template} (e.g.
 * {@code F<A>}); the engine sources it by grounding-by-match (design D2) — unifying the template against an in-scope
 * concrete source and instantiating one concrete port per match. For a template port {@link #type} holds only a
 * representative shape (the template's erasure) and is never used to source the port; grounding replaces it.
 *
 * <p>Each port declares an explicit {@link Sourcing} mode telling the engine how to bind its feeding value, so the
 * driver dispatches on a declared fact rather than reconstructing intent from a name-match or a boolean. The
 * three-argument and template constructors default to {@link Sourcing#REUSE_OR_MINT}; {@link #reuse},
 * {@link #subTarget}, and {@link #ambient} build the other three modes.
 *
 * <p>A port also carries a {@link #key}, meaningful only in {@link Sourcing#AMBIENT} mode: the ambient binding
 * name the engine resolves against the enclosing scope's ambient environment. It is the empty string in every
 * other mode.
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

    /** How the engine binds this port's feeding value — a declared fact, never reconstructed from a name-match. */
    Sourcing sourcing;

    /** The ambient binding key, meaningful only in {@link Sourcing#AMBIENT} mode; the empty string otherwise. */
    String key;

    /** A concrete port whose {@link #type} fully determines the feeding value (no type variable), {@code REUSE_OR_MINT}. */
    public Port(final String name, final TypeMirror type, final Nullability nullness) {
        this(name, type, nullness, null, Sourcing.REUSE_OR_MINT, "");
    }

    /** A type-variable port carrying a {@link PortType} {@code template} the engine grounds by match, {@code REUSE_OR_MINT}. */
    public Port(
            final String name, final TypeMirror type, final Nullability nullness, final @Nullable PortType template) {
        this(name, type, nullness, template, Sourcing.REUSE_OR_MINT, "");
    }

    /** A concrete {@link Sourcing#REUSE} port: bound to an in-scope source or the operation does not apply (never minted). */
    public static Port reuse(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, Sourcing.REUSE, "");
    }

    /** A concrete {@link Sourcing#SUBTARGET} port: a structural sub-target the engine demands at the child location. */
    public static Port subTarget(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, Sourcing.SUBTARGET, "");
    }

    /**
     * A concrete {@link Sourcing#AMBIENT} port: the engine resolves {@code key} against the enclosing scope's
     * ambient environment, verifies the binding's type, and binds it — or reports an error if the key is unbound.
     * {@code key} MUST be non-empty.
     */
    public static Port ambient(final String name, final TypeMirror type, final Nullability nullness, final String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("an AMBIENT port requires a non-empty key");
        }
        return new Port(name, type, nullness, null, Sourcing.AMBIENT, key);
    }

    /**
     * How the engine binds a port's feeding value. A closed set, but <b>extensible</b>: a further binding mode can
     * be added beside these four without changing them or the strategies that declare them.
     */
    public enum Sourcing {

        /** A structural sub-target: the engine mints a fresh demand at the child location and re-demands it. */
        SUBTARGET,

        /** The feeding value must already exist in scope: bound to an in-scope source, or the operation does not apply. */
        REUSE,

        /** The default: bound to an in-scope source, else a fresh intermediate is minted at the output location. */
        REUSE_OR_MINT,

        /**
         * The feeding value is the ambient binding registered under this port's {@link #key} in the enclosing
         * scope's ambient environment. Unlike {@link #REUSE}, an unresolved key is an error, never a quiet
         * non-application.
         */
        AMBIENT
    }
}
