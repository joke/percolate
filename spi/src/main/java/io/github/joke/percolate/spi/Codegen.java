package io.github.joke.percolate.spi;

/**
 * Marker for the codegen-handle family attached to an {@code Operation}. A scalar operation carries an
 * {@link OperationCodegen}; a container operation carries a container codegen provider ({@link ContainerCodegen} /
 * {@link WrapperCodegen}). The composer reads the handle off the operation and asks it for the
 * paradigm-appropriate snippet, holding no container syntax itself.
 */
public interface Codegen {}
