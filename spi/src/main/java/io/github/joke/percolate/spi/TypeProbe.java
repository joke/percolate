package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.DeclKind;
import io.github.joke.percolate.spi.types.TypeDecl;
import io.github.joke.percolate.spi.types.TypeRef;
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
 *
 * <p>Each {@link TypeMirror}-based primitive has a {@link TypeRef}-based counterpart backed by
 * {@link io.github.joke.percolate.spi.types.TypeSpace} (change {@code evict-javax-model}, design D9 transitional
 * bridge) — additive, so both surfaces coexist until callers migrate off mirrors.
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
        return asTypeElement(type, ctx)
                .map(element -> element.getKind() == ElementKind.ENUM)
                .orElse(false);
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

    /** The backing {@link TypeDecl} of a declared type, or empty for a non-declared or edge-less leaf type. */
    public Optional<TypeDecl> asTypeDecl(final TypeRef type, final ResolveCtx ctx) {
        if (!(type instanceof TypeRef.Declared)) {
            return Optional.empty();
        }
        return ctx.typeSpace().decl(((TypeRef.Declared) type).getQualifiedName());
    }

    /** Whether {@code type}'s erasure is the type named {@code fqn} (e.g. {@code java.util.List}). */
    public boolean isType(final TypeRef type, final String fqn, final ResolveCtx ctx) {
        if (!(type instanceof TypeRef.Declared)) {
            return false;
        }
        final var typeSpace = ctx.typeSpace();
        return typeSpace.isSameType(typeSpace.erasure(type), TypeRef.declared(fqn));
    }

    /** Whether {@code type} is an {@code enum} declaration. */
    public boolean isEnum(final TypeRef type, final ResolveCtx ctx) {
        return asTypeDecl(type, ctx)
                .map(decl -> decl.getKind() == DeclKind.ENUM)
                .orElse(false);
    }

    /** A short display name for {@code type}: a declared type's simple name, else its string form. */
    public String simpleName(final TypeRef type) {
        if (type instanceof TypeRef.Declared) {
            final var qualifiedName = ((TypeRef.Declared) type).getQualifiedName();
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
        return type.toString();
    }
}
