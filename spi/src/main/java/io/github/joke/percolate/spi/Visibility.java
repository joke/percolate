package io.github.joke.percolate.spi;

/**
 * Whether a scope input a {@link DirectiveReader} publishes via {@link DirectiveSink#scopeInput} is reachable only
 * within its own scope, or also by name from a descendant scope (design D5/D7 of change
 * {@code decouple-engine-from-strategy-semantics}). Mirrors the engine's own scope-input visibility axis without
 * exposing the engine's graph types to the SPI.
 */
public enum Visibility {

    /** Reachable only within the declaring scope. */
    LOCAL,

    /** Additionally reachable by name from a descendant scope, walking to the nearest ancestor declaring it. */
    INHERITED
}
