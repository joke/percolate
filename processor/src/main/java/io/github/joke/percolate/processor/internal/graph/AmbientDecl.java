package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A scope's ambient-environment entry: a binding {@code key} paired with the base-case declaration — a
 * scope-relative {@code (location, type, nullness)}, exactly an {@link InputDecl} — of the {@code @Ambient}
 * parameter that publishes it. A {@link Scope} declares these lazily ({@link Scope#ambientDecls}); resolution
 * materialises one into a {@code LEAF} source {@link Value} on demand, exactly like an ordinary input.
 */
@Value
public class AmbientDecl {
    String key;
    Location location;
    TypeMirror type;
    Nullability nullness;
}
