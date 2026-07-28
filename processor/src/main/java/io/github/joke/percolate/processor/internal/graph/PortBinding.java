package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Port;
import lombok.Value;

// One port of an AddOperation: the declared Port contract plus the feeding
// io.github.joke.percolate.processor.internal.graph.Value, named by its AddValue identity key (existing or
// created on application).
@Value
public class PortBinding {
    Port port;
    AddValue source;
}
