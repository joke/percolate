package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeRef;
import io.github.joke.percolate.spi.types.TypeRefs;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The structural {@link PortType} → {@link TypeRef} conversion (change {@code evict-javax-model}): a
 * {@link PortType} already carries exactly the shape a {@link TypeRef} models natively — a concrete leaf, a free
 * variable, or a parameterised application whose arguments may themselves be variables — so the conversion is a
 * one-to-one structural walk, no unification or engine state involved.
 */
@UtilityClass
public class PortTypes {

    /** The {@link TypeRef} for {@code template} — the recursive structural conversion. */
    public TypeRef toTypeRef(final PortType template) {
        if (template instanceof PortType.Concrete) {
            return TypeRefs.of(((PortType.Concrete) template).getType());
        }
        if (template instanceof PortType.Var) {
            return TypeRef.variable(variableName(((PortType.Var) template).getIndex()));
        }
        final var app = (PortType.App) template;
        final var args = app.getArgs().stream().map(PortTypes::toTypeRef).collect(Collectors.toUnmodifiableList());
        return TypeRef.declared(app.getErasure().getQualifiedName().toString(), args);
    }

    /** A deterministic, engine-internal variable name — never rendered; a template is always grounded before emission. */
    private String variableName(final int index) {
        return "V" + index;
    }
}
