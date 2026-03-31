package io.github.joke.percolate.processor.graph;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class SourceRootNode {

    private final String name;

    @Override
    public String toString() {
        return "SourceRootNode(" + name + ")";
    }
}
