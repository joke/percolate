package io.github.joke.caffeinate.graph;

import io.github.joke.caffeinate.analysis.MappingMethod;

public final class MethodEdge {
    private final MappingMethod method;

    public MethodEdge(MappingMethod method) {
        this.method = method;
    }

    public MappingMethod getMethod() {
        return method;
    }
}
