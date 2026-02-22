package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.MethodDefinition;
import java.util.Objects;

public final class MethodNode implements GraphNode {

    private final MethodDefinition method;

    public MethodNode(MethodDefinition method) {
        this.method = method;
    }

    public MethodDefinition getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodNode)) {
            return false;
        }
        MethodNode that = (MethodNode) o;
        return Objects.equals(method.getName(), that.method.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(method.getName());
    }

    @Override
    public String toString() {
        return "MethodNode{" + method.getName() + "}";
    }
}
