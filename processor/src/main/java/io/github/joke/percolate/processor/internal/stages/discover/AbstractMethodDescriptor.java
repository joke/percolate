package io.github.joke.percolate.processor.internal.stages.discover;

import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import lombok.Value;

/**
 * A method of a mapper type read as <em>plain data</em> for the abstract-method filter: its {@link Modifier} set and
 * whether it is declared on {@code java.lang.Object} (the reader resolves that against the {@code Object} element, so
 * the filter needs no {@code javax} comparison), plus the opaque {@link ExecutableElement} token carried through for
 * the surviving methods.
 */
@Value
class AbstractMethodDescriptor {
    Set<Modifier> modifiers;
    boolean enclosingIsObject;
    ExecutableElement method;
}
