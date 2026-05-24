package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

@Value
public class ResolvedSegment {
    TypeMirror returnType;
    EdgeCodegen codegen;
    int weight;
}
