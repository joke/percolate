package io.github.joke.percolate.violators;

/** Violates: no method anywhere in percolate is private. */
public class HasPrivateMethod {
    public int visible() {
        return hidden();
    }

    @SuppressWarnings("UnusedMethod")
    private int hidden() {
        return 1;
    }
}
