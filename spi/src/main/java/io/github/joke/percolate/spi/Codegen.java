package io.github.joke.percolate.spi;

/**
 * Marker for the codegen-handle family attached to a {@code REALISED} edge. A scalar edge carries an
 * {@link EdgeCodegen}; a container edge carries a container codegen provider ({@link ContainerCodegen} /
 * {@link WrapperCodegen}). The composer reads the handle off the edge and asks it for the paradigm-appropriate
 * snippet, holding no container syntax itself.
 */
public interface Codegen {}
