package io.github.joke.percolate.spi.types;

import java.util.List;
import lombok.Value;

/**
 * A method's model signature: name, ordered parameter types, and return type — all {@link TypeRef} values, so a
 * signature is itself a value. Signature comparison (e.g. the driver's bind-time self-call rule) is plain
 * {@code equals}; no string keying over {@code TypeMirror.toString()} (the wart this model evicts, see
 * {@code SelfCallGuard}).
 *
 * <p>SPIKE scope: identity fields only (modifiers, nullness, and {@code Origin} land in Phase 1).
 */
@Value
public class MethodSig {
    String name;
    List<TypeRef> parameterTypes;
    TypeRef returnType;
}
