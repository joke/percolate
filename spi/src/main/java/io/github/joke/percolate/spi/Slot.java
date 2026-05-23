package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class Slot {
    String name;
    TypeMirror type;
    int weight;
}
