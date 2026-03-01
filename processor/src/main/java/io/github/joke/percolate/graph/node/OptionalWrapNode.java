package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Wraps a value in Optional<T>. Inline — no helper method registered. */
public final class OptionalWrapNode implements MappingNode {
    private final TypeMirror elementType;
    private final TypeMirror optionalType;

    public OptionalWrapNode(TypeMirror elementType, TypeMirror optionalType) {
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
    public String toString() {
        return "OptionalWrap(" + elementType + ")";
    }
}
