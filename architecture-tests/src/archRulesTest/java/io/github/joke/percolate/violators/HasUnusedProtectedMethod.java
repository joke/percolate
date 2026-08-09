package io.github.joke.percolate.violators;

/** Violates: unused protected methods are marked with VisibleForTesting. No subclass, no annotation. */
public class HasUnusedProtectedMethod {
    protected int widened() {
        return 1;
    }
}
