package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Port;
import lombok.Value;

/**
 * One port of an {@link AddOperation}: the declared {@link Port} contract plus the feeding
 * {@link io.github.joke.percolate.processor.graph.Value}, named by its {@link AddValue} identity key
 * (existing or created on application).
 */
@Value
public class PortBinding {
    Port port;
    AddValue source;
}
