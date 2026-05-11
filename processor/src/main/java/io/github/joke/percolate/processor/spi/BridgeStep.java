package io.github.joke.percolate.processor.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public final class BridgeStep {
    TypeMirror inputType;
    TypeMirror outputType;
    int weight;
    EdgeCodegen codegen;
}
