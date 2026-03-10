package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Unwraps Optional<T> to T. Inline — no helper method registered. */
public final class OptionalUnwrapNode implements MappingNode {
    private final TypeMirror elementType;
    private final TypeMirror optionalType;

    public OptionalUnwrapNode(TypeMirror elementType, TypeMirror optionalType) {
        this.elementType = elementType;
        this.optionalType = optionalType;
    }

    public TypeMirror getElementType() {
        return elementType;
    }

    public TypeMirror getOptionalType() {
        return optionalType;
    }

    @Override
    public TypeMirror inType() {
        return optionalType;
    }

    @Override
    public TypeMirror outType() {
        return elementType;
    }

    @Override
    public String toString() {
        return "OptionalUnwrap(" + elementType + ")";
    }
}
