package io.github.joke.percolate.processor.graph;

import javax.lang.model.type.TypeMirror;

public abstract class PropertyNode {

    private final String name;
    private final TypeMirror type;

    protected PropertyNode(final String name, final TypeMirror type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public TypeMirror type() {
        return type;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }
}
