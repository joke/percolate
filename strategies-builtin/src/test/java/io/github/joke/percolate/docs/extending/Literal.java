package io.github.joke.percolate.docs.extending;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

// tag::annotation[]
// Percolate has never heard of this annotation — LiteralDirectiveReader (shipped alongside it) is what
// teaches the engine to understand it.
@Retention(CLASS)
@Target(METHOD)
public @interface Literal {

    String target();

    String value();
}
// end::annotation[]
