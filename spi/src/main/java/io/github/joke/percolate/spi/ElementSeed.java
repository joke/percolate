package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

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
