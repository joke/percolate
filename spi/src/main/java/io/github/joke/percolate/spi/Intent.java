package io.github.joke.percolate.spi;

/**
 * The single axis the expansion driver branches on for graph shape. A {@link #CONVERSION} step folds in place
 * (it re-types the value at the frontier's position, staying within one flow identity); a {@link #BOUNDARY} step
 * crosses into a new flow identity and opens a sub-group rooted at the frontier.
 */
public enum Intent {
    CONVERSION,
    BOUNDARY
}
