package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A scoped, non-traversable snapshot of one in-scope source value, offered to a strategy via
 * {@link Frontier#candidates()} so it can decide whether it applies. A strategy reads only the candidate's
 * {@link #getType() type}; the driver binds a step's input {@link Slot} back to a graph node by type. No handle
 * from which a strategy could traverse the graph is exposed.
 */
@Value
public class Candidate {

    TypeMirror type;
}
