package io.github.joke.percolate.processor.violators;

import io.github.joke.percolate.Map;

/** Violates: no processor class depends on a user-facing mapping annotation. */
public class ProcessorReadsMappingAnnotation {
    @Map
    public void annotated() {}
}
