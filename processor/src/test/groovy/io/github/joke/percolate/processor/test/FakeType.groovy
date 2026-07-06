package io.github.joke.percolate.processor.test

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVisitor

/**
 * A javac-free {@link TypeMirror} stand-in: a declared type is {@code (identity, args)} — {@code identity} is
 * typically a {@code Stub(TypeElement)} or a plain {@code String} name, so two declared types are structurally
 * comparable without a compiler. An array wraps a {@code component}; a bare marker kind (e.g. {@code WILDCARD}, or a
 * primitive like {@code INT}) carries neither.
 *
 * <p>Only the {@link #declared} factory's result implements {@link DeclaredType}, and only {@link #array}'s
 * implements {@link ArrayType} — matching the real API, where {@code instanceof DeclaredType}/{@code ArrayType} is
 * consistent with {@code getKind()}. This matters because the (boundary-exempt, never-ctx-routed) code that still
 * casts a raw {@code TypeMirror} directly — {@code ValidateConstantDefaultLegalityStage} — relies on that
 * {@code instanceof} check to route primitives/arrays away from the declared-type branch; a type that always
 * answered {@code true} to both interfaces would silently break it. {@code DotRenderer}'s own spec stubs this shape
 * locally rather than sharing this class (change {@code decompose-engine-stages}).
 */
abstract class FakeType implements TypeMirror {

    final TypeKind kind

    protected FakeType(final TypeKind kind) {
        this.kind = kind
    }

    static DeclaredType declared(final Object identity, final TypeMirror... args) {
        new Declared(identity, args.toList())
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

    /** The declared-type erasure identity ({@link #declared} only); throws for any other kind. */
    Object identity() {
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
        private final Object identity
        private final List<TypeMirror> args

        Declared(final Object identity, final List<TypeMirror> args) {
            super(TypeKind.DECLARED)
            this.identity = identity
            this.args = args
        }

        @Override
        Object identity() {
            identity
        }

        @Override
        List<TypeMirror> typeArgs() {
            args
        }

        @Override
        Element asElement() {
            identity instanceof Element ? (Element) identity : FakeElements.simpleElement(identity.toString())
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
            args.empty ? "${identity}" : "${identity}<${args.join(', ')}>"
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
            // Primitives render as their lowercase Java keyword (real javac's TypeMirror.toString() behaviour),
            // not the raw TypeKind enum name — code such as LiteralCoercion's error text depends on this.
            kind.primitive ? kind.toString().toLowerCase(Locale.ROOT) : kind.toString()
        }
    }
}
