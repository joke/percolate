package io.github.joke.percolate.spi.types;

/**
 * An opaque diagnostic-position token carried by model values (design D5). The engine passes it through
 * untouched; only the processor boundary resolves it (via the adapter-populated registry) back to the
 * {@code javax.lang.model} element or annotation mirror {@code Messager} positioning needs. Excluded from
 * every model value's equality — a position is not identity.
 */
public interface Origin {

    /** The absent origin — the default for test-constructed model values. */
    static Origin none() {
        return None.NONE;
    }

    /** The singleton absent origin. */
    enum None implements Origin {
        NONE
    }
}
