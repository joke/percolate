package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.spi.Port.OnMiss.DECLINE;
import static io.github.joke.percolate.spi.Port.OnMiss.MINT;
import static io.github.joke.percolate.spi.Port.OnMiss.REQUIRE;
import static io.github.joke.percolate.spi.Port.Selector.BY_NAME;
import static io.github.joke.percolate.spi.Port.Selector.BY_TYPE;

/**
 * One input of an operation's ordered port signature: the consumer contract a feeding value must satisfy —
 * the port's name, its declared type, its declared nullness, and how the engine binds its feeding value. The
 * port signature lives on the consumer (the operation), never on an edge or a grouping label.
 *
 * <p>A port is <b>concrete</b> by default (use the three-argument constructor): its {@link #type} fully determines
 * the feeding value. A <b>type-variable</b> port additionally carries a {@link PortType} {@link #template} (e.g.
 * {@code F<A>}); the engine sources it by grounding-by-match (design D2) — unifying the template against an in-scope
 * concrete source and instantiating one concrete port per match. For a template port {@link #type} holds only a
 * representative shape (the template's erasure) and is never used to source the port; grounding replaces it.
 *
 * <p>Every port declares how the engine binds its feeding value through <b>two orthogonal axes</b> (design D5 of
 * change {@code decouple-engine-from-strategy-semantics}), so the driver dispatches on declared facts rather than
 * reconstructing intent from a name-match or a boolean, and so no axis names a user-facing feature:
 *
 * <ul>
 *   <li>a {@link Selector}: {@code BY_TYPE} (matched by type and assignment-compatible nullness) or {@code BY_NAME}
 *       (the scope input published under this port's {@link #bindingName});
 *   <li>an {@link OnMiss} rule: {@code DECLINE} (the operation does not apply), {@code MINT} (a fresh intermediate
 *       is minted at the output location) or {@code REQUIRE} (an error, reported by the engine in port vocabulary).
 * </ul>
 *
 * <p>{@link #isSubTarget()} is a distinct third case, not a selection at all: the engine mints a fresh demand at
 * the child location and re-demands it. A sub-target port's {@link #selector} and {@link #onMiss} are {@code null}.
 *
 * <p>{@link #bindingName} is meaningful only for a {@code BY_NAME} port: the scope-input name the engine resolves
 * against the enclosing scope's named inputs. It is the empty string in every other case.
 */
@Value
@AllArgsConstructor
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName") // subTarget backs the unwrapped isSubTarget() accessor
public class Port {

    String name;
    TypeMirror type;
    Nullability nullness;

    /** The variable-carrying shape of this port, or {@code null} for an ordinary concrete port. */
    @Nullable
    PortType template;

    /** Whether this port is the distinct {@code SUBTARGET} case; when {@code true}, {@link #selector}/{@link #onMiss} are {@code null}. */
    boolean subTarget;

    /** How the feeding value is selected; {@code null} for a sub-target port. */
    @Nullable
    Selector selector;

    /** What a selection miss means; {@code null} for a sub-target port. */
    @Nullable
    OnMiss onMiss;

    /** The scope-input name to resolve, meaningful only for a {@code BY_NAME} port; the empty string otherwise. */
    String bindingName;

    /** A concrete port whose {@link #type} fully determines the feeding value (no type variable), {@code BY_TYPE}/{@code MINT}. */
    public Port(final String name, final TypeMirror type, final Nullability nullness) {
        this(name, type, nullness, null, false, BY_TYPE, MINT, "");
    }

    /** A type-variable port carrying a {@link PortType} {@code template} the engine grounds by match, {@code BY_TYPE}/{@code MINT}. */
    public Port(
            final String name, final TypeMirror type, final Nullability nullness, final @Nullable PortType template) {
        this(name, type, nullness, template, false, BY_TYPE, MINT, "");
    }

    /** A concrete {@code BY_TYPE}/{@code MINT} port — the plain-constructor default, named for readability at the call site. */
    public static Port byType(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness);
    }

    /** A concrete {@code BY_TYPE}/{@code DECLINE} port: bound to an in-scope source or the operation does not apply (never minted). */
    public static Port byTypeOrDecline(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, false, BY_TYPE, DECLINE, "");
    }

    /** A concrete sub-target port: a structural sub-target the engine demands at the child location. */
    public static Port subTarget(final String name, final TypeMirror type, final Nullability nullness) {
        return new Port(name, type, nullness, null, true, null, null, "");
    }

    /**
     * A concrete {@code BY_NAME}/{@code REQUIRE} port: the engine resolves {@code bindingName} against the
     * enclosing scope's named inputs, verifies the binding's type, and binds it — or reports an error if the name
     * is unresolvable. {@code bindingName} MUST be non-empty.
     */
    public static Port byName(
            final String name, final TypeMirror type, final Nullability nullness, final String bindingName) {
        if (bindingName.isEmpty()) {
            throw new IllegalArgumentException("a BY_NAME port requires a non-empty binding name");
        }
        return new Port(name, type, nullness, null, false, BY_NAME, REQUIRE, bindingName);
    }

    /**
     * How a port's feeding value is selected. A closed set, but <b>extensible</b>: a further selector can be added
     * beside these two without changing them or the strategies that declare them.
     */
    public enum Selector {

        /** Matched by type and assignment-compatible nullness against an in-scope source. */
        BY_TYPE,

        /** The scope input published under this port's {@link Port#bindingName}. */
        BY_NAME
    }

    /**
     * What a selection miss means. A closed set, but <b>extensible</b>: a further rule can be added beside these
     * three without changing them or the strategies that declare them.
     */
    public enum OnMiss {

        /** The feeding value must already exist in scope: a miss means the operation does not apply. */
        DECLINE,

        /** A miss mints a fresh intermediate of the port's type and nullness at the output location. */
        MINT,

        /** A miss is an error: the engine reports it, in port vocabulary, rather than declining quietly. */
        REQUIRE
    }
}
