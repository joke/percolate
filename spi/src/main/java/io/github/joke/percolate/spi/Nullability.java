package io.github.joke.percolate.spi;

public enum Nullability {
    NULLABLE,
    NON_NULL,
    UNKNOWN;

    public static Nullability join(final Nullability a, final Nullability b) {
        if (either(a, b, NULLABLE)) {
            return NULLABLE;
        }
        if (either(a, b, UNKNOWN)) {
            return UNKNOWN;
        }
        return NON_NULL;
    }

    private static boolean either(final Nullability a, final Nullability b, final Nullability value) {
        return a == value || b == value;
    }
}
