package io.github.joke.percolate.spi.types;

/**
 * The member facts production code filters on — visibility ({@code PUBLIC}/{@code PRIVATE}), staticness,
 * {@code default}-ness, abstractness, and constructor-ness (constructors are {@link MethodSig}s flagged
 * {@code CONSTRUCTOR}). Deliberately not the full {@code javax.lang.model.element.Modifier} set: only what
 * strategies and stages actually read.
 */
public enum MemberFlag {
    PUBLIC,
    PRIVATE,
    STATIC,
    DEFAULT,
    ABSTRACT,
    CONSTRUCTOR
}
