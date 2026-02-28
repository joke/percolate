package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.MethodDefinition;
import javax.lang.model.type.TypeMirror;

public final class MethodCallNode implements MappingNode {
    private final MethodDefinition method;
    private final TypeMirror inType;
    private final TypeMirror outType;

    public MethodCallNode(MethodDefinition method, TypeMirror inType, TypeMirror outType) {
        this.method = method;
        this.inType = inType;
        this.outType = outType;
    }

    public MethodDefinition getMethod() {
        return method;
    }

    public TypeMirror getInType() {
        return inType;
    }

    public TypeMirror getOutType() {
        return outType;
    }

    @Override
    public String toString() {
        return "MethodCall(" + method.getName() + ":" + inType + "->" + outType + ")";
    }
}
