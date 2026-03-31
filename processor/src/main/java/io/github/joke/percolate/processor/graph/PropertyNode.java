package io.github.joke.percolate.processor.graph;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public abstract class PropertyNode {

    private final String name;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getName() + ")";
    }
}
