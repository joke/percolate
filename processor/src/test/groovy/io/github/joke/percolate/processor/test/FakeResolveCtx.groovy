package io.github.joke.percolate.processor.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

/**
 * A real, deterministic, javac-free {@link ResolveCtx}: every seam method is answered structurally over
 * {@link FakeType}, never by consulting a compiler. Preferred over a strict {@code Mock(ResolveCtx)} for
 * algorithmic collaborators (e.g. {@code Grounding}'s structural unification) whose call trees would otherwise need
 * dozens of brittle, argument-matched stubs — a fake with real (if simplified) behaviour is both easier to write
 * against and exercises the same recursive code paths a mock would just stub away.
 */
class FakeResolveCtx implements ResolveCtx {

    @Override
    Types types() {
        throw new UnsupportedOperationException('FakeResolveCtx answers every seam question structurally')
    }

    @Override
    Elements elements() {
        throw new UnsupportedOperationException('FakeResolveCtx answers every seam question structurally')
    }

    @Override
    CallableMethods callableMethods() {
        new CallableMethods() {
            @Override
            Stream<MethodCandidate> producing(final TypeMirror outputType) {
                Stream.empty()
            }
        }
    }

    @Override
    boolean isSameType(final TypeMirror a, final TypeMirror b) {
        if (a.is(b)) {
            return true
        }
        if (!(a instanceof FakeType) || !(b instanceof FakeType)) {
            return false
        }
        final FakeType fa = (FakeType) a
        final FakeType fb = (FakeType) b
        if (fa.kind != fb.kind) {
            return false
        }
        switch (fa.kind) {
            case javax.lang.model.type.TypeKind.ARRAY:
                return isSameType(fa.componentType(), fb.componentType())
            case javax.lang.model.type.TypeKind.DECLARED:
                final List<TypeMirror> aArgs = fa.typeArgs()
                final List<TypeMirror> bArgs = fb.typeArgs()
                return fa.identity() == fb.identity() && aArgs.size() == bArgs.size() &&
                        [aArgs, bArgs].transpose().every { pair -> isSameType(pair[0] as TypeMirror, pair[1] as TypeMirror) }
            default:
                return false
        }
    }

    @Override
    TypeMirror erasure(final TypeMirror type) {
        (type instanceof FakeType && type.kind == javax.lang.model.type.TypeKind.DECLARED)
                ? FakeType.declared(((FakeType) type).identity())
                : type
    }

    @Override
    boolean isDeclared(final TypeMirror type) {
        type instanceof FakeType && type.kind == javax.lang.model.type.TypeKind.DECLARED
    }

    @Override
    boolean isArray(final TypeMirror type) {
        type instanceof FakeType && type.kind == javax.lang.model.type.TypeKind.ARRAY
    }

    @Override
    TypeMirror typeArgument(final TypeMirror type, final int index) {
        ((FakeType) type).typeArgs()[index]
    }

    @Override
    int typeArgumentCount(final TypeMirror type) {
        ((FakeType) type).typeArgs().size()
    }

    @Override
    TypeMirror arrayComponent(final TypeMirror type) {
        ((FakeType) type).componentType()
    }

    @Override
    TypeMirror declaredType(final TypeElement element, final TypeMirror... args) {
        FakeType.declared(element, args)
    }

    @Override
    TypeMirror arrayType(final TypeMirror component) {
        FakeType.array(component)
    }
}
