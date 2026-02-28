package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Wraps a value in Optional<T>. Inline — no helper method registered. */
public final class OptionalWrapNode implements MappingNode {
    private final TypeMirror elementType;

    public OptionalWrapNode(TypeMirror elementType) {
        this.elementType = elementType;
    }

    public TypeMirror getElementType() { return elementType; }

    @Override
    public String toString() { return "OptionalWrap(" + elementType + ")"; }
}
