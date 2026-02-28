package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Integer -> int unboxing. Inline — no helper method registered. */
public final class UnboxingNode implements MappingNode {
    private final TypeMirror inType;
    private final TypeMirror outType;

    public UnboxingNode(TypeMirror inType, TypeMirror outType) {
        this.inType = inType;
        this.outType = outType;
    }

    public TypeMirror getInType() { return inType; }
    public TypeMirror getOutType() { return outType; }

    @Override
    public String toString() { return "Unboxing(" + inType + "->" + outType + ")"; }
}
