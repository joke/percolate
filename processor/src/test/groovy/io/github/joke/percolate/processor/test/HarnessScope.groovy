package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.Scope

final class HarnessScope implements Scope {

    private final String name

    HarnessScope(final String name) {
        this.name = name
    }

    @Override
    String encode() {
        name
    }
}
