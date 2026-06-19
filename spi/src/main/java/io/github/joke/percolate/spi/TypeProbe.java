package io.github.joke.percolate.spi;

import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

/**
 * General type-introspection primitives every strategy otherwise re-rolls — declared-type identity, enum-ness, the
 * backing {@link TypeElement}, and a display name. It holds only kind-agnostic primitives and names no container or
 * conversion kind; the container-flavoured {@link Containers} helper delegates its declared-type matching here rather
 * than re-implementing the erasure match.
 */
@UtilityClass
public class TypeProbe {

    /** The backing {@link TypeElement} of a declared type, or empty for a non-declared (primitive/array/…) type. */
    public Optional<TypeElement> asTypeElement(final TypeMirror type, final ResolveCtx ctx) {
        if (type.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        final var element = ctx.types().asElement(type);
        return element instanceof TypeElement ? Optional.of((TypeElement) element) : Optional.empty();
    }

    /** Whether {@code type}'s erasure is the type named {@code fqn} (e.g. {@code java.util.List}). */
    public boolean isType(final TypeMirror type, final String fqn, final ResolveCtx ctx) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var typeElement = ctx.elements().getTypeElement(fqn);
        if (typeElement == null) {
            return false;
        }
        final var types = ctx.types();
        return types.isSameType(types.erasure(type), types.erasure(typeElement.asType()));
    }

    /** Whether {@code type} is an {@code enum} declaration. */
    public boolean isEnum(final TypeMirror type, final ResolveCtx ctx) {
        return asTypeElement(type, ctx).map(element -> element.getKind() == ElementKind.ENUM).orElse(false);
    }

    /** A short display name for {@code type}: a declared type's simple name, else its string form. */
    public String simpleName(final TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            final var element = ((DeclaredType) type).asElement();
            if (element instanceof TypeElement) {
                return ((TypeElement) element).getSimpleName().toString();
            }
        }
        return type.toString();
    }
}
