package io.github.joke.percolate.outside;

import io.github.joke.percolate.processor.internal.violators.EngineInternal;

/** Violates: no class outside the engine reaches a processor internal package. */
public class OutsiderTouchesEngineInternals {
    public EngineInternal reach() {
        return new EngineInternal();
    }
}
