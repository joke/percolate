package io.github.joke.percolate;

import io.github.joke.percolate.spi.ExpansionStrategy;

/** Violates: the annotations depend on no other percolate module. Directly in the root package. */
public class AnnotationTouchesSpi {
    public Class<ExpansionStrategy> reach() {
        return ExpansionStrategy.class;
    }
}
