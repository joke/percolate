package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

@Value
public final class ElementSeed {
    String role;
    TypeMirror inputType;
    TypeMirror outputType;

    public ElementSeed(final String role, final TypeMirror inputType, final TypeMirror outputType) {
        this.role = role;
        this.inputType = inputType;
        this.outputType = outputType;
    }
}
