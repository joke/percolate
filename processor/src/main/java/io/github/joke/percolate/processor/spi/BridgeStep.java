package io.github.joke.percolate.processor.spi;

import lombok.Value;

@Value
public final class BridgeStep {
    int weight;
    EdgeCodegen codegen;
}
