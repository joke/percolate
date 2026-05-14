package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.Scope;

final class HarnessScope implements Scope {
    private final String name;

    HarnessScope(final String name) {
        this.name = name;
    }

    @Override
    public String encode() {
        return name;
    }
}
