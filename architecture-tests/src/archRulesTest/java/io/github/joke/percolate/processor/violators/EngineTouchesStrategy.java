package io.github.joke.percolate.processor.violators;

import io.github.joke.percolate.spi.builtins.violators.StrategySide;

/** Violates: the engine has no edge to any strategy module. */
public class EngineTouchesStrategy {
    public StrategySide reach() {
        return new StrategySide();
    }
}
