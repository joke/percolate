package io.github.joke.percolate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares one enum-constant override for an abstract enum-to-enum conversion method (e.g.
 * {@code OrderStatus toStatus(MyStatus s)}): the source constant named {@link #source()} maps to the target
 * constant named {@link #target()}. A source constant with no {@code @MapEnum} override maps automatically to an
 * identically-named target constant; {@code @MapEnum} is needed only where the two enums name a constant
 * differently. Repeatable, so one method carries one {@code @MapEnum} per overridden constant.
 */
@Documented
@Target(METHOD)
@Retention(CLASS)
@Repeatable(MapEnumList.class) // Link to the container
public @interface MapEnum {

    /** The source enum constant's simple name, e.g. {@code "NEW"}. */
    String source();

    /** The target enum constant's simple name this source constant maps to, e.g. {@code "CREATED"}. */
    String target();
}
