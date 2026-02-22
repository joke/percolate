package io.github.joke.percolate.graph.edge;

import io.github.joke.percolate.model.MethodDefinition;

/** Edge from TypeNode to TypeNode — calling a mapper method. */
public final class MethodCallEdge implements GraphEdge {

    private final MethodDefinition method;

    public MethodCallEdge(MethodDefinition method) {
        this.method = method;
    }

    public MethodDefinition getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return "MethodCall{" + method.getName() + "}";
    }
}
