package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeRef;
import io.github.joke.percolate.spi.types.TypeRefs;
import javax.lang.model.type.TypeMirror;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One input of an operation's ordered port signature: the consumer contract a feeding value must satisfy —
 * the port's name, its declared type, its declared nullness, and its {@link Sourcing} mode. The port signature
 * lives on the consumer (the operation), never on an edge or a grouping label.
 *
 * <p>A port is <b>concrete</b> by default (use the three-argument constructor): its {@link #type} fully determines
 * the feeding value. A <b>type-variable</b> port additionally carries a {@link #template} — a {@link TypeRef}
 * pattern that embeds a {@link TypeRef.Variable} (e.g. {@code F<A>}); the engine sources it by grounding-by-match
 * (design D2) — {@link io.github.joke.percolate.processor.internal.stages.expand.Grounding} (engine-internal) unifies
 * the template's shape against an in-scope concrete source, capturing the <b>real</b> {@link TypeMirror} each
 * variable binds to (never a structural stand-in — a freshly-grounded generic type must be genuinely
 * compiler-backed, since a hoisted local's declaration and a method override signature demand exact JLS fidelity),
 * then instantiates one concrete port per match. For a template port {@link #type} holds only a representative
 * shape (the template's erasure) and is never used to source the port; grounding replaces it.
 *
 * <p>{@link #typeRef} is the owned-model counterpart of {@link #type} (change {@code evict-javax-model}, design D9):
 * derived automatically, never supplied by a caller. For a concrete port it is {@link TypeRefs#of}({@link #type});
 * for a template port it is {@link #template} itself — already the faithful structural shape (variables embedded in
 * place), not just the template's representative erasure.
 *
 * <p>Each port declares an explicit {@link Sourcing} mode telling the engine how to bind its feeding value, so the
 * driver dispatches on a declared fact rather than reconstructing intent from a name-match or a boolean. The
 * three-argument and template constructors default to {@link Sourcing#REUSE_OR_MINT}; {@link #reuse} and
 * {@link #subTarget} build the other two modes.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Port {

    String name;
    TypeMirror type;
    Nullability nullness;

    /** The variable-carrying shape of this port, or {@code null} for an ordinary concrete port. */
    @Nullable
    TypeRef template;

    /** How the engine binds this port's feeding value — a declared fact, never reconstructed from a name-match. */
    Sourcing sourcing;

    /** The owned-model counterpart of {@link #type} — see the class doc; always derived, never supplied directly. */
    TypeRef typeRef;

    /** A concrete port whose {@link #type} fully determines the feeding value (no type variable), {@code REUSE_OR_MINT}. */
    public Port(final String name, final TypeMirror type, final Nullability nullness) {
        this(name, type, nullness, null, Sourcing.REUSE_OR_MINT);
    }

    /** A type-variable port carrying a {@link TypeRef} {@code template} the engine grounds by match, {@code REUSE_OR_MINT}. */
    public Port(
            final String name, final TypeMirror type, final Nullability nullness, final @Nullable TypeRef template) {
        this(name, type, nullness, template, Sourcing.REUSE_OR_MINT);
    }

    /** Builds a port with an explicit {@link Sourcing}, deriving {@link #typeRef} from {@code template} or {@code type}. */
    public Port(
            final String name,
            final TypeMirror type,
            final Nullability nullness,
            final @Nullable TypeRef template,
            final Sourcing sourcing) {
        this(name, type, nullness, template, sourcing, template == null ? TypeRefs.of(type) : template);
    }

    /** A concrete {@link Sourcing#REUSE} port: bound to an in-scope source or the operation does not apply (never minted). */
    public static Port reuse(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, Sourcing.REUSE);
    }

    /** A concrete {@link Sourcing#SUBTARGET} port: a structural sub-target the engine demands at the child location. */
    public static Port subTarget(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, Sourcing.SUBTARGET);
    }

    /**
     * How the engine binds a port's feeding value. A closed set, but <b>extensible</b>: a future binding mode (e.g.
     * binding a port by name to an ambient captured source) can be added beside these three without changing them or
     * the strategies that declare them.
     */
    public enum Sourcing {

        /** A structural sub-target: the engine mints a fresh demand at the child location and re-demands it. */
        SUBTARGET,

        /** The feeding value must already exist in scope: bound to an in-scope source, or the operation does not apply. */
        REUSE,

        /** The default: bound to an in-scope source, else a fresh intermediate is minted at the output location. */
        REUSE_OR_MINT
    }
}
