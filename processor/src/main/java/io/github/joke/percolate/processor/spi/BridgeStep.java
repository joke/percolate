package io.github.joke.percolate.processor.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

@Value
public final class BridgeStep {
    TypeMirror inputType;
    TypeMirror outputType;
    int weight;
    EdgeCodegen codegen;
}
