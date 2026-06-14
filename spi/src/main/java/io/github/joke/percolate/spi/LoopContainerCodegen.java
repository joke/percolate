package io.github.joke.percolate.spi;

/**
 * Marker for the loop-paradigm codegen handle a {@link Container} MAY supply. The loop backend
 * ({@code codegen.style=loop}) is architecturally enabled but not built in this change; concrete loop snippet
 * methods are added when that backend lands.
 */
public interface LoopContainerCodegen extends Codegen {}
