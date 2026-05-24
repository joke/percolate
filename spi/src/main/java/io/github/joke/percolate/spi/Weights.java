package io.github.joke.percolate.spi;

import lombok.experimental.UtilityClass;

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
    public static final int SENTINEL_UNREALISED = Integer.MAX_VALUE / 2;

    public static boolean isSentinel(final int weight) {
        return weight >= SENTINEL_UNREALISED;
    }
}
