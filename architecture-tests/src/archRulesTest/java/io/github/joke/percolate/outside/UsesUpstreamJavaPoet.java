package io.github.joke.percolate.outside;

import com.palantir.javapoet.FakeSpec;

/** Violates: no production class imports the unrelocated upstream JavaPoet package. */
public class UsesUpstreamJavaPoet {
    public FakeSpec reach() {
        return new FakeSpec();
    }
}
