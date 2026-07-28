package io.github.joke.percolate.processor.internal.stages.discover;

import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import lombok.Value;

// A method of a mapper type read as plain data for the abstract-method filter: its Modifier set and whether it
// is declared on java.lang.Object (the reader resolves that against the Object element, so the filter needs no
// javax comparison), plus the opaque ExecutableElement token carried through for the surviving methods.
@Value
class AbstractMethodDescriptor {
    Set<Modifier> modifiers;
    boolean enclosingIsObject;
    ExecutableElement method;
}
