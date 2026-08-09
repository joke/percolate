package io.github.joke.percolate.test.violators;

import io.github.joke.percolate.spi.builtins.violators.StrategySide;

/** Violates: the compile harness is strategy-agnostic. */
public class HarnessTouchesStrategy {
    public StrategySide reach() {
        return new StrategySide();
    }
}
