package io.github.joke.percolate;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a mapper method parameter whose value is threaded down the mapping call chain rather than derived
 * from the object being mapped. An {@code @Ambient} parameter plays both roles at once, determined by
 * position rather than a second annotation: it is <strong>bound</strong> from the enclosing ambient
 * environment when one offers a matching key, and it is <strong>published</strong> into the ambient
 * environment of its own subtree either way. A top-level mapper method has no enclosing environment, so its
 * {@code @Ambient} parameters are supplied by the caller and are pure providers.
 *
 * <h2>Keying</h2>
 *
 * <p>The binding key is the parameter's own simple name, e.g. {@code @Ambient Person simon} publishes and
 * binds under key {@code "simon"}. {@link #value()} overrides the key, so a consumer may declare its own
 * parameter under a different name while still binding the same key: {@code @Ambient("simon") Person p}.
 *
 * <h2>Type verification</h2>
 *
 * <p>The key alone identifies a binding — the declared type is never part of it. Instead, the declared type
 * is <em>verified</em> against the binding's type wherever the key resolves; a same-key pair whose types are
 * not assignable is reported as an error naming the key and both types, never treated as a silent non-match.
 *
 * <h2>Loud failure</h2>
 *
 * <p>An {@code @Ambient} port whose key resolves to no binding is reported as an error naming the unbound key,
 * rather than silently declining the way {@code BY_TYPE}/{@code DECLINE} does. A duplicate key within one method's
 * parameters is likewise an error, positioned at the second {@code @Ambient}.
 */
@Documented
@Target(PARAMETER)
@Retention(CLASS)
public @interface Ambient {

    /**
     * The binding key. Defaults to the empty string, meaning the parameter's own simple name is the key. A
     * non-empty value overrides the key, letting a consumer rename its own parameter while still binding the
     * same key a provider published.
     */
    String value() default "";
}
