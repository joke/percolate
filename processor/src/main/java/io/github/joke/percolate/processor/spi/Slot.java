package io.github.joke.percolate.processor.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

@Value
public final class Slot {
    String name;
    TypeMirror type;
    int weight;
}
