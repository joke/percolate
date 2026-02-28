package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Unwraps Optional<T> to T. Inline — no helper method registered. */
public final class OptionalUnwrapNode implements MappingNode {
    private final TypeMirror elementType;

    public OptionalUnwrapNode(TypeMirror elementType) {
        this.elementType = elementType;
    }

    public TypeMirror getElementType() {
        return elementType;
    }

    @Override
    public String toString() {
        return "OptionalUnwrap(" + elementType + ")";
    }
}
