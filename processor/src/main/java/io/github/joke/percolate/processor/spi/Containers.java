package io.github.joke.percolate.processor.spi;

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

    public boolean isList(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.List", ctx);
    }

    public boolean isSet(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Set", ctx);
    }

    public boolean isCollection(final TypeMirror t, final ResolveCtx ctx) {
        return isDeclaredTypeErasureMatch(t, "java.util.Collection", ctx);
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
