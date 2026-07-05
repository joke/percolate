package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link Containers} is a thin, source-compatible forwarder onto the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}): its container-kind predicates delegate straight to {@code ctx}, unit-tested here
 * against a mocked {@link ResolveCtx} with opaque {@link TypeMirror} tokens. {@link Containers#isArray},
 * {@link Containers#isReferenceType}, {@link Containers#typeArgument} and {@link Containers#arrayComponentType}
 * never needed a compiler-backed {@code ResolveCtx} and are exercised directly over mocked
 * {@link TypeMirror}/{@link DeclaredType}/{@link ArrayType} tokens. No javac.
 */
@Tag('unit')
class ContainersSpec extends Specification {

    ResolveCtx ctx = Mock()

    def 'isOptional delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isOptional(yes) >> true
        ctx.isOptional(no) >> false

        expect:
        Containers.isOptional(yes, ctx)
        !Containers.isOptional(no, ctx)
    }

    def 'isList delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isList(yes) >> true
        ctx.isList(no) >> false

        expect:
        Containers.isList(yes, ctx)
        !Containers.isList(no, ctx)
    }

    def 'isSet delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isSet(yes) >> true
        ctx.isSet(no) >> false

        expect:
        Containers.isSet(yes, ctx)
        !Containers.isSet(no, ctx)
    }

    def 'isStream delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isStream(yes) >> true
        ctx.isStream(no) >> false

        expect:
        Containers.isStream(yes, ctx)
        !Containers.isStream(no, ctx)
    }

    def 'isCollection delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isCollection(yes) >> true
        ctx.isCollection(no) >> false

        expect:
        Containers.isCollection(yes, ctx)
        !Containers.isCollection(no, ctx)
    }

    def 'isIterable delegates to the seam'() {
        TypeMirror yes = Mock()
        TypeMirror no = Mock()
        ctx.isIterable(yes) >> true
        ctx.isIterable(no) >> false

        expect:
        Containers.isIterable(yes, ctx)
        !Containers.isIterable(no, ctx)
    }

    def 'isArray recognises array types and rejects others, with no ResolveCtx involved'() {
        TypeMirror array = Mock()
        array.kind >> TypeKind.ARRAY
        TypeMirror notArray = Mock()
        notArray.kind >> TypeKind.DECLARED

        expect:
        Containers.isArray(array)
        !Containers.isArray(notArray)
    }

    def 'isReferenceType accepts declared/array/type-variable kinds and rejects primitives'() {
        expect:
        Containers.isReferenceType(kindOf(TypeKind.DECLARED))
        Containers.isReferenceType(kindOf(TypeKind.ARRAY))
        Containers.isReferenceType(kindOf(TypeKind.TYPEVAR))
        !Containers.isReferenceType(kindOf(TypeKind.INT))
    }

    def 'typeArgument returns the requested generic type argument'() {
        TypeMirror first = Mock()
        TypeMirror second = Mock()
        DeclaredType declaredType = Mock()
        declaredType.kind >> TypeKind.DECLARED
        declaredType.typeArguments >> [first, second]

        expect:
        Containers.typeArgument(declaredType, 0).is(first)
        Containers.typeArgument(declaredType, 1).is(second)
    }

    def 'typeArgument throws on non-declared types'() {
        when:
        Containers.typeArgument(kindOf(TypeKind.INT), 0)

        then:
        thrown(IllegalArgumentException)
    }

    def 'typeArgument throws on out-of-bounds index'() {
        DeclaredType declaredType = Mock()
        declaredType.kind >> TypeKind.DECLARED
        declaredType.typeArguments >> [Mock(TypeMirror)]

        when:
        Containers.typeArgument(declaredType, 5)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'arrayComponentType returns the array element type'() {
        TypeMirror component = Mock()
        ArrayType arrayType = Mock()
        arrayType.kind >> TypeKind.ARRAY
        arrayType.componentType >> component

        expect:
        Containers.arrayComponentType(arrayType).is(component)
    }

    def 'arrayComponentType throws on non-array types'() {
        when:
        Containers.arrayComponentType(kindOf(TypeKind.DECLARED))

        then:
        thrown(IllegalArgumentException)
    }

    private TypeMirror kindOf(final TypeKind kind) {
        TypeMirror type = Mock()
        type.kind >> kind
        type
    }
}
