package io.github.joke.percolate.processor.internal.stages.discover;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A member of a mapper type read as <em>plain data</em> for the callable-method filter: its {@link ElementKind},
 * parameter count, and whether it is declared on {@code java.lang.Object} (the reader resolves that, so the filter
 * needs no {@code javax} comparison), plus the opaque return-type {@link TypeMirror} and {@link ExecutableElement}
 * tokens carried through for the surviving candidates. The filter no longer special-cases {@code @Ambient}
 * parameters (design D7 of change {@code decouple-engine-from-strategy-semantics}: the processor reads no
 * user-facing annotation) — {@code MethodCallBridge} itself, being SPI-side, filters a candidate's non-ambient
 * parameter count.
 */
@Value
class CandidateDescriptor {
    ElementKind kind;
    int parameterCount;
    boolean enclosingIsObject;
    TypeMirror returnType;
    ExecutableElement method;
}
