package io.github.joke.percolate.spi;

public enum Nullability {
    NULLABLE,
    NON_NULL,
    UNKNOWN;

    public static Nullability join(final Nullability a, final Nullability b) {
        if (a == NULLABLE || b == NULLABLE) {
            return NULLABLE;
        }
        if (a == UNKNOWN || b == UNKNOWN) {
            return UNKNOWN;
        }
        return NON_NULL;
    }
}
