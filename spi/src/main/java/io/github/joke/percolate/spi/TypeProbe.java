package io.github.joke.percolate.spi;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

/**
 * General type-introspection primitives, forwarding to the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}) — kept as a public utility for source compatibility; new call sites should prefer the
 * {@code ResolveCtx} methods directly.
 */
@UtilityClass
public class TypeProbe {

    /** The backing {@link TypeElement} of a declared type, or empty for a non-declared (primitive/array/…) type. */
    public Optional<TypeElement> asTypeElement(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.asTypeElement(type);
    }

    /** Whether {@code type}'s erasure is the type named {@code fqn} (e.g. {@code java.util.List}). */
    public boolean isType(final TypeMirror type, final String fqn, final ResolveCtx ctx) {
        return ctx.isType(type, fqn);
    }

    /** Whether {@code type} is an {@code enum} declaration. */
    public boolean isEnum(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isEnum(type);
    }

    /** A short display name for {@code type}: a declared type's simple name, else its string form. */
    public String simpleName(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.simpleName(type);
    }
}
