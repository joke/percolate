package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A scoped, non-traversable snapshot of one in-scope source value, offered to a strategy via
 * {@link Demand#candidates()} so it can decide whether it applies. A strategy reads the candidate's
 * {@link #getType() type} and {@link #getNullness() nullness} (the latter lets a nullness-crossing strategy fire on a
 * {@code (nullable candidate, non-null demand)} pair); the driver binds an operation's input port back to a graph
 * Value by type and nullness. No handle from which a strategy could traverse the graph is exposed.
 */
@Value
public class Candidate {

    TypeMirror type;
    Nullability nullness;
}
