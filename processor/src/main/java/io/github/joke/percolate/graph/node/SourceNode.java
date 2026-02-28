package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

public final class SourceNode implements MappingNode {
    private final String paramName;
    private final TypeMirror type;

    public SourceNode(String paramName, TypeMirror type) {
        this.paramName = paramName;
        this.type = type;
    }

    public String getParamName() { return paramName; }
    public TypeMirror getType() { return type; }

    @Override
    public String toString() { return "Source(" + paramName + ":" + type + ")"; }
}
