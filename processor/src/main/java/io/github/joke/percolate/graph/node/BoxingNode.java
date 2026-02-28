package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** int -> Integer boxing. Inline — no helper method registered. */
public final class BoxingNode implements MappingNode {
    private final TypeMirror inType;
    private final TypeMirror outType;

    public BoxingNode(TypeMirror inType, TypeMirror outType) {
        this.inType = inType;
        this.outType = outType;
    }

    public TypeMirror getInType() {
        return inType;
    }

    public TypeMirror getOutType() {
        return outType;
    }

    @Override
    public String toString() {
        return "Boxing(" + inType + "->" + outType + ")";
    }
}
