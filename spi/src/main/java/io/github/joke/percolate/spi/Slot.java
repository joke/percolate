package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

@Value
public class Slot {
    String name;
    TypeMirror type;
    int weight;
}
