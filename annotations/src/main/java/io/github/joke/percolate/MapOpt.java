package io.github.joke.percolate;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;

@Retention(CLASS)
public @interface MapOpt {

    MapOptKey key();

    String value();
}
