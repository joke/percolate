package io.github.joke.percolate.processor.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public final class Step {
    TypeMirror produces;
    int weight;
    EdgeCodegen codegen;
}
