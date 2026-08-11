package io.github.joke.percolate.spi;

import org.jetbrains.annotations.VisibleForTesting;

public enum Nullability {
    NULLABLE,
    NON_NULL,
    UNKNOWN;

    public static Nullability join(final Nullability a, final Nullability b) {
        if (NULLABLE.either(a, b)) {
            return NULLABLE;
        }
        if (UNKNOWN.either(a, b)) {
            return UNKNOWN;
        }
        return NON_NULL;
    }

    /** Whether either {@code a} or {@code b} is this constant — the receiver is the value being looked for. */
    @VisibleForTesting
    boolean either(final Nullability a, final Nullability b) {
        return a == this || b == this;
    }
}
