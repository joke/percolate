package io.github.joke.percolate.processor.graph;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Weights {
    public static final int NOOP = 0;
    public static final int STEP = 1;
    public static final int COPY = 2;
    public static final int EXPENSIVE = 3;
    public static final int SENTINEL_UNREALISED = Integer.MAX_VALUE / 2;

    public static boolean isSentinel(final int weight) {
        return weight >= SENTINEL_UNREALISED;
    }
}
