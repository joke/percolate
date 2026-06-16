package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/** The {@code (type, nullness)} of a source-path segment, resolved forward by the pure, non-mutating typing walk. */
@Value
class Typing {
    TypeMirror type;
    Nullability nullness;
}
