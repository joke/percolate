package io.github.joke.percolate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a {@code default} method on a {@code @Mapper} interface as an internal routing target.
 *
 * <p>During expansion, when a type transformation from {@code X} to {@code Y} is demanded and a
 * {@code @Routable} default method with signature {@code Y(X)} exists on the same mapper, the
 * expander SHALL emit a call to that method as the transformation. Abstract methods annotated with
 * {@code @Routable} are rejected at compile time.
 *
 * @see Map#using()
 */
@Documented
@Target(METHOD)
@Retention(CLASS)
public @interface Routable {}
