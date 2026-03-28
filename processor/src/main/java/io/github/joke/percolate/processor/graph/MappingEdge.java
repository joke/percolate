package io.github.joke.percolate.processor.graph;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MappingEdge {
    private final Type type;

    public enum Type {
        DIRECT
    }

    @Override
    public String toString() {
        return "MappingEdge(type=" + type + ")";
    }
}
