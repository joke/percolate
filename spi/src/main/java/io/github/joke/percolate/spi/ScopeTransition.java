package io.github.joke.percolate.spi;

/**
 * Declares how a {@link BridgeStep} relates to element scope.
 *
 * <p>The driver consults this enum in two places: candidacy (whether the step matches the current frontier) and
 * input-node allocation (where to place the freshly allocated input node). It is the only scope-aware engine surface;
 * strategies declare their scope transition in the {@link BridgeStep} and let the driver materialise the consequences.
 */
public enum ScopeTransition {
    /** Input and output live at the same {@code Location} as the frontier. */
    PRESERVING,
    /** Output lives at {@code ElementLocation(role)}; input typically lives at regular scope. */
    ENTERING,
    /** Input lives at {@code ElementLocation(role)}; output lives at the surrounding (typically regular) scope. */
    EXITING
}
