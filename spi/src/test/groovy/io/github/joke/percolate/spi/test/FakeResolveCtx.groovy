package io.github.joke.percolate.spi.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

/**
 * A real, deterministic, javac-free {@link ResolveCtx}: every leaf type-algebra and member-reflection question is
 * answered structurally over {@link FakeType}, never by consulting a compiler. The higher-level default methods
 * ({@code isType}/{@code isList}/{@code isCollection}/{@code isEnum}/{@code isReferenceType}/…) are deliberately
 * <b>not</b> overridden, so a spec built on this fake exercises their real composition logic — the point of
 * {@code ResolveCtxSpec}, which pins the seam's own default-method bodies. Register the structural facts a test
 * needs ({@link #assignable}, {@link #named}, {@link #members}) before use; everything else falls out of
 * {@link FakeType}'s shape, exactly like {@code isSameType} elsewhere in this codebase (see
 * {@code io.github.joke.percolate.processor.test.FakeResolveCtx}, the engine-side sibling this mirrors).
 */
class FakeResolveCtx implements ResolveCtx {

    private static final Map<TypeKind, String> WRAPPER_FQN = [
            (TypeKind.BOOLEAN): 'java.lang.Boolean',
            (TypeKind.BYTE)   : 'java.lang.Byte',
            (TypeKind.SHORT)  : 'java.lang.Short',
            (TypeKind.CHAR)   : 'java.lang.Character',
            (TypeKind.INT)    : 'java.lang.Integer',
            (TypeKind.LONG)   : 'java.lang.Long',
            (TypeKind.FLOAT)  : 'java.lang.Float',
            (TypeKind.DOUBLE) : 'java.lang.Double',
    ]

    private final List<List<TypeMirror>> assignableFacts = []
    private final Map<String, TypeElement> namedElements = [:]
    private final Map<TypeElement, String> elementNames = [:]
    private final Map<TypeElement, List<? extends Element>> memberFacts = [:]

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

    /** Declares that {@code from} is assignable to {@code to} (e.g. {@code List}'s erasure → {@code Collection}'s). */
    void assignable(final TypeMirror from, final TypeMirror to) {
        assignableFacts << [from, to]
    }

    /** Registers {@code element} as resolvable by {@link #typeElementNamed} under {@code fqn}. */
    void named(final String fqn, final TypeElement element) {
        namedElements[fqn] = element
        elementNames[element] = fqn
    }

    /** Declares {@code parent}'s members, as returned by {@link #membersOf}. */
    void members(final TypeElement parent, final List<? extends Element> parentMembers) {
        memberFacts[parent] = parentMembers
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
            case TypeKind.ARRAY:
                return isSameType(fa.componentType(), fb.componentType())
            case TypeKind.DECLARED:
                final List<TypeMirror> aArgs = fa.typeArgs()
                final List<TypeMirror> bArgs = fb.typeArgs()
                return fa.identity() == fb.identity() && aArgs.size() == bArgs.size() &&
                        [aArgs, bArgs].transpose().every { pair -> isSameType(pair[0] as TypeMirror, pair[1] as TypeMirror) }
            default:
                // same kind, no further structure (primitives, NONE, …) — kind-equality is type-equality here
                return true
        }
    }

    @Override
    boolean isAssignable(final TypeMirror a, final TypeMirror b) {
        isSameType(a, b) || assignableFacts.any { isSameType(it[0], a) && isSameType(it[1], b) }
    }

    @Override
    TypeMirror erasure(final TypeMirror type) {
        (type instanceof FakeType && type.kind == TypeKind.DECLARED)
                ? FakeType.declared(((FakeType) type).identity())
                : type
    }

    @Override
    boolean isDeclared(final TypeMirror type) {
        type instanceof FakeType && type.kind == TypeKind.DECLARED
    }

    @Override
    boolean isArray(final TypeMirror type) {
        type instanceof FakeType && type.kind == TypeKind.ARRAY
    }

    @Override
    boolean isTypeVariable(final TypeMirror type) {
        type instanceof FakeType && type.kind == TypeKind.TYPEVAR
    }

    @Override
    TypeMirror typeArgument(final TypeMirror type, final int index) {
        if (!isDeclared(type)) {
            throw new IllegalArgumentException("Not a declared type: ${type}")
        }
        final args = ((FakeType) type).typeArgs()
        if (index < 0 || index >= args.size()) {
            throw new IndexOutOfBoundsException("Index ${index} out of bounds for type arguments of ${type}")
        }
        args[index]
    }

    @Override
    int typeArgumentCount(final TypeMirror type) {
        if (!isDeclared(type)) {
            throw new IllegalArgumentException("Not a declared type: ${type}")
        }
        ((FakeType) type).typeArgs().size()
    }

    @Override
    TypeMirror arrayComponent(final TypeMirror type) {
        if (!isArray(type)) {
            throw new IllegalArgumentException("Not an array type: ${type}")
        }
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

    @Override
    TypeMirror boxed(final TypeMirror primitive) {
        final fqn = WRAPPER_FQN[primitive.kind]
        namedElements[fqn]?.asType()
    }

    @Override
    TypeMirror unboxed(final TypeMirror wrapper) {
        final fqn = elementNames[((FakeType) wrapper).identity()]
        FakeType.marker(WRAPPER_FQN.find { it.value == fqn }.key)
    }

    @Override
    TypeMirror primitiveType(final TypeKind kind) {
        FakeType.marker(kind)
    }

    @Override
    TypeElement typeElementNamed(final String fqn) {
        namedElements[fqn]
    }

    @Override
    Optional<TypeElement> asTypeElement(final TypeMirror type) {
        isDeclared(type) ? Optional.of(((FakeType) type).identity()) : Optional.empty()
    }

    @Override
    TypeMirror superclassOf(final TypeMirror type) {
        asTypeElement(type).map { it.superclass }.orElse(FakeType.marker(TypeKind.NONE))
    }

    @Override
    Stream<? extends Element> membersOf(final TypeElement parent) {
        (memberFacts[parent] ?: []).stream()
    }
}
