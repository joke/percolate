package io.github.joke.percolate;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

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
 * <h2>Presence</h2>
 *
 * <p>{@code source}, {@code constant}, {@code defaultValue}, {@code format}, and {@code zone} are all optional
 * members with an empty-string default, but presence is never decided by reading that default back: the processor
 * reads each member through {@code AnnotationMirror.getElementValues()}, which reports only the members an author
 * actually wrote. A member is therefore present exactly when it was written — including as the empty string
 * {@code ""} — and absent otherwise; the default exists only so the member may be omitted, and is never itself a
 * meaningful value.
 */
@Documented
@Target(METHOD)
@Retention(CLASS)
@Repeatable(MapList.class) // Link to the container
public @interface Map {

    /** The target member path this directive produces, e.g. {@code "address.zip"}. Always required. */
    String target();

    /**
     * The source path the value is moved from, e.g. {@code "person.firstName"}. Optional: absent for a
     * {@link #constant()} directive.
     */
    String source() default "";

    /**
     * A fixed literal value for the target, produced with no {@link #source()}. Present — including as the empty
     * string {@code ""} — exactly when actually written. A present {@code constant} is mutually exclusive with a
     * present {@code source}.
     */
    String constant() default "";

    /**
     * A fallback applied only when the {@link #source()} value is absent (a {@code null} reference scalar or an empty
     * {@code Optional}); it never replaces a present source value. A present {@code defaultValue} requires a present
     * {@code source} and is illegal with a {@code constant}.
     */
    String defaultValue() default "";

    /**
     * A {@link java.time.format.DateTimeFormatter}-style pattern used to parse a {@code String} source into, or
     * render a {@code String} from, a temporal target. Applies only where a {@code String} crosses with a date/time
     * type; declared on any other pairing, it has no effect and is reported.
     */
    String format() default "";

    /**
     * A {@link java.time.ZoneId} id (e.g. {@code "Europe/Berlin"}) used by a cross-family temporal conversion (an
     * "absolute" instant-based type crossing to or from a "local" wall-time type). Applies only where the winning
     * conversion crosses the zone bridge; declared on any other pairing, it has no effect and is reported.
     */
    String zone() default "";
}
