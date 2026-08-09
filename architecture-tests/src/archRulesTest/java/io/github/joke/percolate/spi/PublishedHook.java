package io.github.joke.percolate.spi;

/**
 * Exempt: a concrete protected method on the published spi surface is the extension contract, so it needs
 * no @VisibleForTesting even with no visible subclass. Mirrors Container#containerOf / #wrapNullness, whose
 * only production overriders live in strategies-builtin and are invisible to spi's own evaluation.
 */
public class PublishedHook {
    protected int hook() {
        return 1;
    }
}
