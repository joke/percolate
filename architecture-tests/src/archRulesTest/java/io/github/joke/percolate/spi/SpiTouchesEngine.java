package io.github.joke.percolate.spi;

import io.github.joke.percolate.processor.violators.EngineTouchesStrategy;

/** Violates: spi depends on neither the engine nor any strategy. Directly in the spi package. */
public class SpiTouchesEngine {
    public EngineTouchesStrategy reach() {
        return new EngineTouchesStrategy();
    }
}
