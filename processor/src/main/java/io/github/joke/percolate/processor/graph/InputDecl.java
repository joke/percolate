package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A scope's base-case input declaration: a scope-relative {@code (location, type, nullness)} — an {@link AddValue}
 * lacking only its scope. A {@link Scope} declares these lazily ({@link Scope#inputDecls}); the driver materialises
 * one into a {@code LEAF} source {@link io.github.joke.percolate.processor.graph.Value} on demand (idempotent through
 * the {@code valueFor} dedup index) only when a port reuses it.
 *
 * <p>Carrying the declaration without minting a {@link io.github.joke.percolate.processor.graph.Value} is what lets an
 * unreferenced input — an unused method parameter, or an unused container element — never enter the graph, while its
 * binding (the parameter name / lambda variable) still exists for code generation.
 */
@Value
public class InputDecl {
    Location location;
    TypeMirror type;
    Nullability nullness;
}
