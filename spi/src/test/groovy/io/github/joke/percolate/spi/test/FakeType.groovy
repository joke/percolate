package io.github.joke.percolate.spi.test

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVisitor

/**
 * A javac-free {@link TypeMirror} stand-in for {@code spi} unit specs that exercise {@link
 * io.github.joke.percolate.spi.ResolveCtx}'s own default-method composition (change {@code type-query-seam}): a
 * declared type is {@code (element, args)} — {@code element} is whatever {@link TypeElement} test double the
 * fixture was built over (typically a {@code Mock(TypeElement)}), so two declared types are structurally
 * comparable without a compiler. An array wraps a {@code component}; a bare marker kind (e.g. a primitive like
 * {@code INT}, or {@code NONE}) carries neither.
 *
 * <p>Only the {@link #declared} factory's result implements {@link DeclaredType}, and only {@link #array}'s
 * implements {@link ArrayType} — matching the real API, where {@code instanceof DeclaredType}/{@code ArrayType} is
 * consistent with {@code getKind()}.
 */
abstract class FakeType implements TypeMirror {

    final TypeKind kind

    protected FakeType(final TypeKind kind) {
        this.kind = kind
    }

    static DeclaredType declared(final TypeElement element, final TypeMirror... args) {
        new Declared(element, args.toList())
    }

    static ArrayType array(final TypeMirror component) {
        new Arr(component)
    }

    static TypeMirror marker(final TypeKind kind) {
        new Marker(kind)
    }

    @Override
    TypeKind getKind() {
        kind
    }

    /** The declared-type backing element ({@link #declared} only); throws for any other kind. */
    TypeElement identity() {
        throw new UnsupportedOperationException("not a declared type: ${this}")
    }

    /** The declared-type type arguments ({@link #declared} only); throws for any other kind. */
    List<TypeMirror> typeArgs() {
        throw new UnsupportedOperationException("not a declared type: ${this}")
    }

    /** The array component type ({@link #array} only); throws for any other kind. */
    TypeMirror componentType() {
        throw new UnsupportedOperationException("not an array type: ${this}")
    }

    @Override
    def <R, P> R accept(final TypeVisitor<R, P> v, final P p) {
        null
    }

    @Override
    List<? extends AnnotationMirror> getAnnotationMirrors() {
        []
    }

    @Override
    def <A extends java.lang.annotation.Annotation> A getAnnotation(final Class<A> annotationType) {
        null
    }

    @Override
    def <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(final Class<A> annotationType) {
        (A[]) []
    }

    private static final class Declared extends FakeType implements DeclaredType {
        private final TypeElement element
        private final List<TypeMirror> args

        Declared(final TypeElement element, final List<TypeMirror> args) {
            super(TypeKind.DECLARED)
            this.element = element
            this.args = args
        }

        @Override
        TypeElement identity() {
            element
        }

        @Override
        List<TypeMirror> typeArgs() {
            args
        }

        @Override
        Element asElement() {
            element
        }

        @Override
        TypeMirror getEnclosingType() {
            FakeType.marker(TypeKind.NONE)
        }

        @Override
        List<? extends TypeMirror> getTypeArguments() {
            args
        }

        @Override
        String toString() {
            args.empty ? "${element}" : "${element}<${args.join(', ')}>"
        }
    }

    private static final class Arr extends FakeType implements ArrayType {
        private final TypeMirror component

        Arr(final TypeMirror component) {
            super(TypeKind.ARRAY)
            this.component = component
        }

        @Override
        TypeMirror getComponentType() {
            component
        }

        @Override
        TypeMirror componentType() {
            component
        }

        @Override
        String toString() {
            "${component}[]"
        }
    }

    private static final class Marker extends FakeType {
        Marker(final TypeKind kind) {
            super(kind)
        }

        @Override
        String toString() {
            kind.primitive ? kind.toString().toLowerCase(Locale.ROOT) : kind.toString()
        }
    }
}
