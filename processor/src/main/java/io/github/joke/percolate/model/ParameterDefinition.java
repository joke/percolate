package io.github.joke.percolate.model;

import javax.lang.model.type.TypeMirror;

public final class ParameterDefinition {

    private final String name;
    private final TypeMirror type;

    public ParameterDefinition(String name, TypeMirror type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }
}
