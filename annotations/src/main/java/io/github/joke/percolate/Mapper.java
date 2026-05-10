package io.github.joke.percolate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Documented
@Target(TYPE)
@Retention(CLASS)
public @interface Mapper {}
