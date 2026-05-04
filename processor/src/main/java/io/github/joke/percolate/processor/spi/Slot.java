package io.github.joke.percolate.processor.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public final class Slot {
    String name;
    TypeMirror type;
    int weight;
}
