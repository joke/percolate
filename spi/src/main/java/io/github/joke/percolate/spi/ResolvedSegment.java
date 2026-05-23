package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class ResolvedSegment {
    TypeMirror returnType;
    EdgeCodegen codegen;
    int weight;
}
