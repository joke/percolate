package io.github.joke.percolate.processor.internal.stages.discover;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * A member of a mapper type read as <em>plain data</em> for the callable-method filter: its {@link ElementKind},
 * parameter count, and whether it is declared on {@code java.lang.Object} (the reader resolves that, so the filter
 * needs no {@code javax} comparison), plus the opaque return-type {@link TypeMirror} and {@link ExecutableElement}
 * tokens carried through for the surviving candidates.
 */
@Value
class CandidateDescriptor {
    ElementKind kind;
    int parameterCount;
    boolean enclosingIsObject;
    TypeMirror returnType;
    ExecutableElement method;
}
