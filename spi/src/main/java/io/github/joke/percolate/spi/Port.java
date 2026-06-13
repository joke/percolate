package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * One input of an operation's ordered port signature: the consumer contract a feeding value must satisfy —
 * the port's name, its declared type, and its declared nullness. The port signature lives on the consumer
 * (the operation), never on an edge or a grouping label.
 */
@Value
public class Port {
    String name;
    TypeMirror type;
    Nullability nullness;
}
