package io.github.joke.percolate;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Documented
@Target(METHOD)
@Retention(CLASS)
@Repeatable(MapList.class) // Link to the container
public @interface Map {

     String target();

     String source();

}
