package io.github.joke.percolate.spi;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

/**
 * Container-kind predicates and structural accessors, forwarding to the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}) — kept as a public utility for source compatibility; new call sites should prefer the
 * {@code ResolveCtx} methods directly. The four purely-syntactic accessors ({@link #isArray}, {@link #isReferenceType},
 * {@link #typeArgument}, {@link #arrayComponentType}) never needed a compiler-backed {@code ResolveCtx} and keep their
 * original no-{@code ResolveCtx} shape.
 */
@UtilityClass
public class Containers {

    public boolean isOptional(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isOptional(t);
    }

    public boolean isStream(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isStream(t);
    }

    /** Whether {@code element} is a reference type — i.e. usable as a generic type argument (not a primitive). */
    public boolean isReferenceType(final TypeMirror element) {
        final var kind = element.getKind();
        return kind == DECLARED || kind == ARRAY || kind == TYPEVAR;
    }

    public boolean isList(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isList(t);
    }

    public boolean isSet(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isSet(t);
    }

    public boolean isCollection(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isCollection(t);
    }

    public boolean isIterable(final TypeMirror t, final ResolveCtx ctx) {
        return ctx.isIterable(t);
    }

    public boolean isArray(final TypeMirror t) {
        return t.getKind() == ARRAY;
    }

    public TypeMirror typeArgument(final TypeMirror declaredType, final int index) {
        if (declaredType.getKind() != DECLARED) {
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
        if (arrayType.getKind() != ARRAY) {
            throw new IllegalArgumentException("Not an array type: " + arrayType);
        }
        return ((ArrayType) arrayType).getComponentType();
    }
}
