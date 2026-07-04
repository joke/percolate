package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.TypeDecl
import io.github.joke.percolate.spi.types.TypeRef
import io.github.joke.percolate.spi.types.TypeSpace
import io.github.joke.percolate.spi.types.test.TestTypes
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ContainersSpec extends Specification {

    private static final TypeRef E = TypeRef.variable('E')
    // Literal edges (design D8): java.util.List/Set -> Collection -> Iterable, hand-built rather than
    // reflected off the real JDK interfaces, whose own methods use wildcards the v1 model doesn't represent.
    private static final TypeDecl LIST_DECL =
            TypeDecl.of('java.util.List', ['E'], [TypeRef.declared('java.util.Collection', E)])
    private static final TypeDecl SET_DECL =
            TypeDecl.of('java.util.Set', ['E'], [TypeRef.declared('java.util.Collection', E)])
    private static final TypeDecl COLLECTION_DECL =
            TypeDecl.of('java.util.Collection', ['E'], [TypeRef.declared('java.lang.Iterable', E)])

    @Shared ResolveCtx ctx = new SimpleResolveCtx()
    @Shared ResolveCtx typeRefCtx = new TypeSpaceResolveCtx(TypeSpace.of(LIST_DECL, SET_DECL, COLLECTION_DECL))
    @Shared TypeMirror optionalOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
    @Shared TypeMirror setOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
    @Shared TypeMirror stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
    @Shared TypeMirror streamOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
    @Shared TypeRef setOfStringRef = TypeRef.declared('java.util.Set', TestTypes.STRING)
    @Shared TypeRef streamOfStringRef = TypeRef.declared('java.util.stream.Stream', TestTypes.STRING)
    @Shared TypeRef optionalOfStringRef = TypeRef.declared('java.util.Optional', TestTypes.STRING)
    @Shared TypeRef stringArrayRef = TypeRef.array(TestTypes.STRING)

    def 'isOptional recognises Optional<X> and rejects others'() {
        expect:
        Containers.isOptional(optionalOfString, ctx)
        !Containers.isOptional(TypeUniverse.STRING, ctx)
        !Containers.isOptional(TypeUniverse.LIST_OF_STRING, ctx)
        !Containers.isOptional(TypeUniverse.INT, ctx)
    }

    def 'isList recognises List<X> and rejects others'() {
        expect:
        Containers.isList(TypeUniverse.LIST_OF_STRING, ctx)
        !Containers.isList(setOfString, ctx)
        !Containers.isList(TypeUniverse.STRING, ctx)
    }

    def 'isSet recognises Set<X> and rejects others'() {
        expect:
        Containers.isSet(setOfString, ctx)
        !Containers.isSet(TypeUniverse.LIST_OF_STRING, ctx)
        !Containers.isSet(TypeUniverse.STRING, ctx)
    }

    def 'isCollection recognises Collection-typed declarations'() {
        expect:
        Containers.isCollection(TypeUniverse.LIST_OF_STRING, ctx)
        Containers.isCollection(setOfString, ctx)
        !Containers.isCollection(TypeUniverse.STRING, ctx)
    }

    def 'isIterable recognises Iterable subtypes and rejects others'() {
        expect:
        Containers.isIterable(TypeUniverse.LIST_OF_STRING, ctx)
        Containers.isIterable(setOfString, ctx)
        !Containers.isIterable(TypeUniverse.STRING, ctx)
        !Containers.isIterable(TypeUniverse.INT, ctx)
    }

    def 'isArray recognises array types and rejects others'() {
        expect:
        Containers.isArray(stringArray)
        !Containers.isArray(TypeUniverse.STRING)
        !Containers.isArray(TypeUniverse.LIST_OF_STRING)
    }

    def 'typeArgument returns the requested generic type argument'() {
        expect:
        ctx.types().isSameType(Containers.typeArgument(TypeUniverse.LIST_OF_STRING, 0), TypeUniverse.STRING)
        ctx.types().isSameType(Containers.typeArgument(optionalOfString, 0), TypeUniverse.STRING)
    }

    def 'typeArgument throws on non-declared types'() {
        when:
        Containers.typeArgument(TypeUniverse.INT, 0)
        then:
        thrown(IllegalArgumentException)
    }

    def 'typeArgument throws on out-of-bounds index'() {
        when:
        Containers.typeArgument(TypeUniverse.LIST_OF_STRING, 5)
        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'arrayComponentType returns the array element type'() {
        expect:
        ctx.types().isSameType(Containers.arrayComponentType(stringArray), TypeUniverse.STRING)
    }

    def 'arrayComponentType throws on non-array types'() {
        when:
        Containers.arrayComponentType(TypeUniverse.STRING)
        then:
        thrown(IllegalArgumentException)
    }

    def 'isStream recognises Stream<X> and rejects others'() {
        expect:
        Containers.isStream(streamOfString, ctx)
        !Containers.isStream(TypeUniverse.LIST_OF_STRING, ctx)
        !Containers.isStream(TypeUniverse.STRING, ctx)
        !Containers.isStream(TypeUniverse.INT, ctx)
    }

    def 'isReferenceType accepts declared and array types and rejects primitives'() {
        expect:
        Containers.isReferenceType(TypeUniverse.STRING)
        Containers.isReferenceType(stringArray)
        !Containers.isReferenceType(TypeUniverse.INT)
    }

    def 'isOptional(TypeRef) recognises Optional<X> and rejects others'() {
        expect:
        Containers.isOptional(optionalOfStringRef, typeRefCtx)
        !Containers.isOptional(TestTypes.STRING, typeRefCtx)
        !Containers.isOptional(TestTypes.LIST_OF_STRING, typeRefCtx)
        !Containers.isOptional(TestTypes.INT, typeRefCtx)
    }

    def 'isList(TypeRef) recognises List<X> and rejects others'() {
        expect:
        Containers.isList(TestTypes.LIST_OF_STRING, typeRefCtx)
        !Containers.isList(setOfStringRef, typeRefCtx)
        !Containers.isList(TestTypes.STRING, typeRefCtx)
    }

    def 'isSet(TypeRef) recognises Set<X> and rejects others'() {
        expect:
        Containers.isSet(setOfStringRef, typeRefCtx)
        !Containers.isSet(TestTypes.LIST_OF_STRING, typeRefCtx)
        !Containers.isSet(TestTypes.STRING, typeRefCtx)
    }

    def 'isCollection(TypeRef) recognises Collection-typed declarations'() {
        expect:
        Containers.isCollection(TestTypes.LIST_OF_STRING, typeRefCtx)
        Containers.isCollection(setOfStringRef, typeRefCtx)
        !Containers.isCollection(TestTypes.STRING, typeRefCtx)
    }

    def 'isIterable(TypeRef) recognises Iterable subtypes and rejects others'() {
        expect:
        Containers.isIterable(TestTypes.LIST_OF_STRING, typeRefCtx)
        Containers.isIterable(setOfStringRef, typeRefCtx)
        !Containers.isIterable(TestTypes.STRING, typeRefCtx)
        !Containers.isIterable(TestTypes.INT, typeRefCtx)
    }

    def 'isArray(TypeRef) recognises array types and rejects others'() {
        expect:
        Containers.isArray(stringArrayRef)
        !Containers.isArray(TestTypes.STRING)
        !Containers.isArray(TestTypes.LIST_OF_STRING)
    }

    def 'typeArgument(TypeRef) returns the requested generic type argument'() {
        expect:
        Containers.typeArgument(TestTypes.LIST_OF_STRING, 0) == TestTypes.STRING
        Containers.typeArgument(optionalOfStringRef, 0) == TestTypes.STRING
    }

    def 'typeArgument(TypeRef) throws on non-declared types'() {
        when:
        Containers.typeArgument(TestTypes.INT, 0)
        then:
        thrown(IllegalArgumentException)
    }

    def 'typeArgument(TypeRef) throws on out-of-bounds index'() {
        when:
        Containers.typeArgument(TestTypes.LIST_OF_STRING, 5)
        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'arrayComponentType(TypeRef) returns the array element type'() {
        expect:
        Containers.arrayComponentType(stringArrayRef) == TestTypes.STRING
    }

    def 'arrayComponentType(TypeRef) throws on non-array types'() {
        when:
        Containers.arrayComponentType(TestTypes.STRING)
        then:
        thrown(IllegalArgumentException)
    }

    def 'isStream(TypeRef) recognises Stream<X> and rejects others'() {
        expect:
        Containers.isStream(streamOfStringRef, typeRefCtx)
        !Containers.isStream(TestTypes.LIST_OF_STRING, typeRefCtx)
        !Containers.isStream(TestTypes.STRING, typeRefCtx)
        !Containers.isStream(TestTypes.INT, typeRefCtx)
    }

    def 'isReferenceType(TypeRef) accepts declared, array and variable types and rejects primitives'() {
        expect:
        Containers.isReferenceType(TestTypes.STRING)
        Containers.isReferenceType(stringArrayRef)
        Containers.isReferenceType(TypeRef.variable('T'))
        !Containers.isReferenceType(TestTypes.INT)
    }

    private static final class SimpleResolveCtx implements ResolveCtx {
        @Override
        javax.lang.model.util.Elements elements() {
            TypeUniverse.elements()
        }

        @Override
        javax.lang.model.util.Types types() {
            TypeUniverse.types()
        }

        @Override
        CallableMethods callableMethods() {
            null
        }
    }

    private static final class TypeSpaceResolveCtx implements ResolveCtx {
        private final TypeSpace typeSpace

        TypeSpaceResolveCtx(final TypeSpace typeSpace) {
            this.typeSpace = typeSpace
        }

        @Override
        javax.lang.model.util.Elements elements() {
            throw new UnsupportedOperationException()
        }

        @Override
        javax.lang.model.util.Types types() {
            throw new UnsupportedOperationException()
        }

        @Override
        CallableMethods callableMethods() {
            null
        }

        @Override
        TypeSpace typeSpace() {
            typeSpace
        }
    }
}
