package io.github.joke.percolate.graph.node;

import java.util.Objects;
import javax.lang.model.type.TypeMirror;

public final class TypeNode implements GraphNode {

    private final TypeMirror type;
    private final String label;

    public TypeNode(TypeMirror type, String label) {
        this.type = type;
        this.label = label;
    }

    public TypeMirror getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeNode)) {
            return false;
        }
        TypeNode that = (TypeNode) o;
        return Objects.equals(type.toString(), that.type.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(type.toString());
    }

    @Override
    public String toString() {
        return "TypeNode{" + label + " : " + type + "}";
    }
}
