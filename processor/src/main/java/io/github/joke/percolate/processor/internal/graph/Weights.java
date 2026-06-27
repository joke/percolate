package io.github.joke.percolate.processor.internal.graph;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Weights {
    public static final int SENTINEL_UNREALISED = Integer.MAX_VALUE / 2;

    public static boolean isSentinel(final int weight) {
        return weight >= SENTINEL_UNREALISED;
    }
}
