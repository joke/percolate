package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

// A scope's base-case input declaration: a scope-relative (location, type, nullness) — an AddValue lacking only
// its scope — plus a name and a Visibility (design D5 of change decouple-engine-from-strategy-semantics). A
// Scope declares these lazily (Scope.inputDecls); the driver materialises one into a LEAF source Value on
// demand (idempotent through the valueFor dedup index) only when a port reuses it.
//
// Carrying the declaration without minting a Value is what lets an unreferenced input — an unused method
// parameter, or an unused container element — never enter the graph, while its binding (the parameter name /
// lambda variable) still exists for code generation. The name and visibility let a BY_NAME port select this
// declaration by name, from its own scope or (when Visibility.INHERITED) from a descendant.
@Value
public class InputDecl {
    Location location;
    TypeMirror type;
    Nullability nullness;
    String name;
    Visibility visibility;
}
