package io.github.joke.percolate.processor.internal.stages.generate;

import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

import jakarta.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import lombok.NoArgsConstructor;

/**
 * The pure assembly decisions {@link AssembleMapperType} makes on plain inputs, split out from the
 * {@code TypeName.get(mirror)} render/{@code Filer}-write leaf so they unit-test without a compiler: the finality of a
 * generated {@code public} member (a class or a method) and of a parameter, driven by the {@code percolate.*.final}
 * switches, and whether a mapper's {@link ElementKind} means the impl {@code implements} an interface or
 * {@code extends} a class. It reads no {@code javax.lang.model} structure, only the {@code ElementKind} enum and
 * booleans.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
final class MapperTypeDecisions {

    /** {@code public}, plus {@code final} when the matching {@code classes.final}/{@code methods.final} switch is on. */
    Modifier[] publicModifiers(final boolean makeFinal) {
        return makeFinal ? new Modifier[] {PUBLIC, FINAL} : new Modifier[] {PUBLIC};
    }

    /** {@code final} when {@code parameters.final} is on, otherwise no modifier at all. */
    Modifier[] parameterModifiers(final boolean makeFinal) {
        return makeFinal ? new Modifier[] {FINAL} : new Modifier[] {};
    }

    /** An interface mapper is {@code implements}ed; any other kind (a class) is {@code extends}ed. */
    boolean isInterface(final ElementKind kind) {
        return kind == INTERFACE;
    }
}
