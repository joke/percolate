package io.github.joke.percolate.spi;

import lombok.experimental.UtilityClass;

// A pure constant roster after SENTINEL_UNREALISED/isSentinel's removal (design D8, change
// decouple-engine-from-strategy-semantics); PMD's DataClass heuristic doesn't apply to a UtilityClass.
@SuppressWarnings("PMD.DataClass")
@UtilityClass
public class Weights {
    public static final int NOOP = 0;
    public static final int STEP = 1;
    public static final int METHOD = 1;
    public static final int STEP_GETTER = 1;
    public static final int STEP_METHOD = 2;
    public static final int STEP_FIELD = 3;
    public static final int COPY = 2;
    public static final int CONTAINER = 2;
    public static final int EXPENSIVE = 3;
}
