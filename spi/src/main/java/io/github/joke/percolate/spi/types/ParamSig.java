package io.github.joke.percolate.spi.types;

import io.github.joke.percolate.spi.Nullability;
import lombok.Value;

/**
 * A method parameter's model signature: name, type, and <b>resolved</b> nullness. Nullness is data resolved
 * once at the discovery boundary (where the {@code NullabilityResolver} has the real mirrors in hand) — never
 * re-derived downstream.
 */
@Value
public class ParamSig {
    String name;
    TypeRef type;
    Nullability nullness;
}
