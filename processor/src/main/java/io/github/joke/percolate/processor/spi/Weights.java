package io.github.joke.percolate.processor.spi;

public final class Weights {
    public static final int NOOP = 0;
    public static final int STEP = 1;
    public static final int COPY = 2;
    public static final int EXPENSIVE = 3;
    public static final int SENTINEL_UNREALISED = Integer.MAX_VALUE / 2;

    private Weights() {
        throw new UnsupportedOperationException();
    }
}
