package io.github.joke.percolate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares how one target member is produced. A directive supplies its value in exactly one of two ways:
 *
 * <ul>
 *   <li>from a {@link #source() source} path — the value is <em>moved</em> from a method parameter, optionally with
 *       a {@link #defaultValue() defaultValue} fallback for when that source is absent (a {@code null} reference or
 *       an empty {@code Optional});</li>
 *   <li>from a {@link #constant() constant} literal — the value is <em>supplied</em> with no source at all.</li>
 * </ul>
 *
 * <p>Exactly one of {@code source} and {@code constant} must be present, and {@code defaultValue} is meaningful only
 * alongside a {@code source} (never with a {@code constant}).
 *
 * <h2>Presence and the {@link #UNSET} sentinel</h2>
 *
 * <p>{@code source}, {@code constant}, and {@code defaultValue} all default to the {@link #UNSET} sentinel rather
 * than to {@code ""}, because an empty string is a <em>legitimate</em> constant or default value (e.g.
 * {@code constant = ""}). A member is therefore <strong>present</strong> exactly when
 * {@code !Map.UNSET.equals(value)} and <strong>absent</strong> when it equals {@link #UNSET}. Presence MUST NOT be
 * decided with {@link String#isEmpty()}: an empty string is present, not absent.
 */
@Documented
@Target(METHOD)
@Retention(CLASS)
@Repeatable(MapList.class) // Link to the container
public @interface Map {

    /**
     * Collision-proof "not specified" sentinel for {@link #source()}, {@link #constant()}, and
     * {@link #defaultValue()}. The token is bracketed by NUL characters, which a Java source string literal can
     * never contain, so it can never collide with a value an author writes; it stays recognizable if it ever
     * surfaces in a diagnostic. Compare with {@code Map.UNSET.equals(value)} — never {@link String#isEmpty()}.
     */
    String UNSET = "\0io.github.joke.percolate.Map.UNSET\0";

    /** The target member path this directive produces, e.g. {@code "address.zip"}. Always required. */
    String target();

    /**
     * The source path the value is moved from, e.g. {@code "person.firstName"}. Optional: defaults to {@link #UNSET}
     * (absent) for a {@link #constant()} directive. Present exactly when {@code !Map.UNSET.equals(source())}.
     */
    String source() default UNSET;

    /**
     * A fixed literal value for the target, produced with no {@link #source()}. Defaults to {@link #UNSET} (absent).
     * Present — including as the empty string {@code ""} — exactly when {@code !Map.UNSET.equals(constant())}. A
     * present {@code constant} is mutually exclusive with a present {@code source}.
     */
    String constant() default UNSET;

    /**
     * A fallback applied only when the {@link #source()} value is absent (a {@code null} reference scalar or an empty
     * {@code Optional}); it never replaces a present source value. Defaults to {@link #UNSET} (absent). Present —
     * including as the empty string {@code ""} — exactly when {@code !Map.UNSET.equals(defaultValue())}. A present
     * {@code defaultValue} requires a present {@code source} and is illegal with a {@code constant}.
     */
    String defaultValue() default UNSET;

    /**
     * A {@link java.time.format.DateTimeFormatter}-style pattern used to parse a {@code String} source into, or
     * render a {@code String} from, a temporal target. Defaults to {@link #UNSET} (absent). Present — including as
     * the empty string {@code ""} — exactly when {@code !Map.UNSET.equals(format())}. Applies only where a
     * {@code String} crosses with a date/time type; declared on any other pairing, it has no effect and is reported.
     */
    String format() default UNSET;

    /**
     * A {@link java.time.ZoneId} id (e.g. {@code "Europe/Berlin"}) used by a cross-family temporal conversion (an
     * "absolute" instant-based type crossing to or from a "local" wall-time type). Defaults to {@link #UNSET}
     * (absent). Present — including as the empty string {@code ""} — exactly when {@code !Map.UNSET.equals(zone())}.
     * Applies only where the winning conversion crosses the zone bridge; declared on any other pairing, it has no
     * effect and is reported.
     */
    String zone() default UNSET;
}
