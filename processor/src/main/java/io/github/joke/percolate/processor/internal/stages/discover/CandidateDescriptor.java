package io.github.joke.percolate.processor.internal.stages.discover;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

// A member of a mapper type read as plain data for the callable-method filter: its ElementKind, parameter
// count, and whether it is declared on java.lang.Object (the reader resolves that, so the filter needs no javax
// comparison), plus the opaque return-type TypeMirror and ExecutableElement tokens carried through for the
// surviving candidates. The filter no longer special-cases @Ambient parameters (design D7 of change decouple-
// engine-from-strategy-semantics: the processor reads no user-facing annotation) — MethodCallBridge itself,
// being SPI-side, filters a candidate's non-ambient parameter count.
@Value
class CandidateDescriptor {
    ElementKind kind;
    int parameterCount;
    boolean enclosingIsObject;
    TypeMirror returnType;
    ExecutableElement method;
}
