package io.github.joke.percolate.processor.internal.stages.generate;

import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

import jakarta.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import lombok.NoArgsConstructor;

// The pure assembly decisions AssembleMapperType makes on plain inputs, split out from the TypeName.get(mirror)
// render/Filer-write leaf so they unit-test without a compiler: the finality of a generated public member (a
// class or a method) and of a parameter, driven by the percolate.*.final switches, and whether a mapper's
// ElementKind means the impl implements an interface or extends a class. It reads no javax.lang.model
// structure, only the ElementKind enum and booleans.
@NoArgsConstructor(onConstructor_ = @Inject)
final class MapperTypeDecisions {

    // public, plus final when the matching classes.final/methods.final switch is on.
    Modifier[] publicModifiers(final boolean makeFinal) {
        return makeFinal ? new Modifier[] {PUBLIC, FINAL} : new Modifier[] {PUBLIC};
    }

    // final when parameters.final is on, otherwise no modifier at all.
    Modifier[] parameterModifiers(final boolean makeFinal) {
        return makeFinal ? new Modifier[] {FINAL} : new Modifier[] {};
    }

    // An interface mapper is implementsed; any other kind (a class) is extendsed.
    boolean isInterface(final ElementKind kind) {
        return kind == INTERFACE;
    }
}
