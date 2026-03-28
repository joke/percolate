package io.github.joke.percolate.processor.model;

import javax.lang.model.type.TypeMirror;

public abstract class ReadAccessor {

    private final String name;
    private final TypeMirror type;

    protected ReadAccessor(final String name, final TypeMirror type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public TypeMirror type() {
        return type;
    }
}
