package io.github.joke.percolate.spi.builtins.violators;

/**
 * NOT exempt: spi.builtins shares the spi package root but is an internal module, not the published
 * contract, so an unused unannotated protected method there still violates.
 */
public class BuiltinsUnusedProtected {
    protected int widened() {
        return 1;
    }
}
