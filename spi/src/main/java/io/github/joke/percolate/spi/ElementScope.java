package io.github.joke.percolate.spi;

/**
 * The element-scope crossing of a container {@link Intent#BOUNDARY} step, carried in {@link ExpansionStep#getScope()}
 * and present only on container boundaries. {@link #ENTERING} means the step's output lives at element scope
 * (e.g. iterate a sequence into its elements); {@link #EXITING} means the step's input lives at element scope
 * (e.g. collect elements back into a container).
 */
public enum ElementScope {
    ENTERING,
    EXITING
}
