package io.github.joke.percolate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Target(METHOD)
@Retention(CLASS)
public @interface MapEnumList {
    MapEnum[] value(); // Must be named 'value'
}
