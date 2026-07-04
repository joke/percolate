package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeRef;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

/**
 * Each {@link TypeMirror}-based predicate has a {@link TypeRef}-based counterpart backed by
 * {@link io.github.joke.percolate.spi.types.TypeSpace} (change {@code evict-javax-model}, design D9 transitional
 * bridge) — additive, so both surfaces coexist until callers migrate off mirrors.
 */
@UtilityClass
public class Containers {

    public boolean isOptional(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Optional", ctx);
    }

    public boolean isStream(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.stream.Stream", ctx);
    }

    /** Whether {@code element} is a reference type — i.e. usable as a generic type argument (not a primitive). */
    public boolean isReferenceType(final TypeMirror element) {
        final var kind = element.getKind();
        return kind == TypeKind.DECLARED || kind == TypeKind.ARRAY || kind == TypeKind.TYPEVAR;
    }

    public boolean isList(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.List", ctx);
    }

    public boolean isSet(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Set", ctx);
    }

    public boolean isCollection(final TypeMirror t, final ResolveCtx ctx) {
        if (t.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var collectionElement = ctx.elements().getTypeElement("java.util.Collection");
        if (collectionElement == null) {
            return false;
        }
        final var types = ctx.types();
        return types.isAssignable(types.erasure(t), types.erasure(collectionElement.asType()));
    }

    public boolean isIterable(final TypeMirror t, final ResolveCtx ctx) {
        if (t.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var iterableElement = ctx.elements().getTypeElement("java.lang.Iterable");
        if (iterableElement == null) {
            return false;
        }
        final var types = ctx.types();
        return types.isAssignable(types.erasure(t), types.erasure(iterableElement.asType()));
    }

    public boolean isArray(final TypeMirror t) {
        return t.getKind() == TypeKind.ARRAY;
    }

    public TypeMirror typeArgument(final TypeMirror declaredType, final int index) {
        if (declaredType.getKind() != TypeKind.DECLARED) {
            throw new IllegalArgumentException("Not a declared type: " + declaredType);
        }
        final var declared = (DeclaredType) declaredType;
        final var typeArgs = declared.getTypeArguments();
        if (index < 0 || index >= typeArgs.size()) {
            throw new IndexOutOfBoundsException(
                    "Index " + index + " out of bounds for type arguments of " + declaredType);
        }
        return typeArgs.get(index);
    }

    public TypeMirror arrayComponentType(final TypeMirror arrayType) {
        if (arrayType.getKind() != TypeKind.ARRAY) {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        return ((ArrayType) arrayType).getComponentType();
    }

    private boolean isDeclaredTypeErasureMatch(final TypeMirror t, final String fqn, final ResolveCtx ctx) {
        return TypeProbe.isType(t, fqn, ctx);
    }

    public boolean isOptional(final TypeRef t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Optional", ctx);
    }

    public boolean isStream(final TypeRef t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.stream.Stream", ctx);
    }

    /** Whether {@code type} is a reference type — i.e. usable as a generic type argument (not a primitive). */
    public boolean isReferenceType(final TypeRef type) {
        return type instanceof TypeRef.Declared || type instanceof TypeRef.Array || type instanceof TypeRef.Variable;
    }

    public boolean isList(final TypeRef t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.List", ctx);
    }

    public boolean isSet(final TypeRef t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Set", ctx);
    }

    public boolean isCollection(final TypeRef t, final ResolveCtx ctx) {
        return isAssignableToDeclared(t, "java.util.Collection", ctx);
    }

    public boolean isIterable(final TypeRef t, final ResolveCtx ctx) {
        return isAssignableToDeclared(t, "java.lang.Iterable", ctx);
    }

    public boolean isArray(final TypeRef t) {
        return t instanceof TypeRef.Array;
    }

    public TypeRef typeArgument(final TypeRef declaredType, final int index) {
        if (!(declaredType instanceof TypeRef.Declared)) {
            throw new IllegalArgumentException("Not a declared type: " + declaredType);
        }
        final var args = ((TypeRef.Declared) declaredType).getArgs();
        if (index < 0 || index >= args.size()) {
            throw new IndexOutOfBoundsException(
                    "Index " + index + " out of bounds for type arguments of " + declaredType);
        }
        return args.get(index);
    }

    public TypeRef arrayComponentType(final TypeRef arrayType) {
        if (!(arrayType instanceof TypeRef.Array)) {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        return ((TypeRef.Array) arrayType).getComponent();
    }

    private boolean isDeclaredTypeErasureMatch(final TypeRef t, final String fqn, final ResolveCtx ctx) {
        return TypeProbe.isType(t, fqn, ctx);
    }

    private boolean isAssignableToDeclared(final TypeRef t, final String fqn, final ResolveCtx ctx) {
        if (!(t instanceof TypeRef.Declared)) {
            return false;
        }
        final var typeSpace = ctx.typeSpace();
        return typeSpace.isAssignable(typeSpace.erasure(t), TypeRef.declared(fqn));
    }
}
