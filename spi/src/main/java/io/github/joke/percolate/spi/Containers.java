package io.github.joke.percolate.spi;

import java.util.Optional;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Containers {

    public boolean isOptional(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Optional", ctx);
    }

    public boolean isStream(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.stream.Stream", ctx);
    }

    /**
     * The element a {@code .stream()} over {@code t} yields — the structural bridge that lets the generic stream
     * strategy name its port from a non-stream candidate without knowing any container kind: a {@code Collection},
     * array, {@code Optional}, or {@code Stream} element type, else empty. A non-{@code Collection} reactive
     * container would extend this (or declare its element through an SPI hook) when one lands.
     */
    public Optional<TypeMirror> streamElement(final TypeMirror t, final ResolveCtx ctx) {
        if (isArray(t)) {
            return Optional.of(arrayComponentType(t));
        }
        if (t.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        if (isStream(t, ctx) || isOptional(t, ctx) || isCollection(t, ctx)) {
            final var args = ((DeclaredType) t).getTypeArguments();
            return args.isEmpty() ? Optional.empty() : Optional.of(args.get(0));
        }
        return Optional.empty();
    }

    /** {@code Stream<element>} for a reference {@code element}, or empty when no such type can be formed. */
    public Optional<TypeMirror> streamOf(final TypeMirror element, final ResolveCtx ctx) {
        final var stream = ctx.elements().getTypeElement("java.util.stream.Stream");
        if (stream == null || !isReferenceType(element)) {
            return Optional.empty();
        }
        return Optional.of(ctx.types().getDeclaredType(stream, element));
    }

    private boolean isReferenceType(final TypeMirror element) {
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
        if (t.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var typeElement = ctx.elements().getTypeElement(fqn);
        if (typeElement == null) {
            return false;
        }
        final var types = ctx.types();
        return types.isSameType(types.erasure(t), types.erasure(typeElement.asType()));
    }
}
