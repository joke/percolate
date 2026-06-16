package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ContainersSpec extends Specification {

    @Shared ResolveCtx ctx = new SimpleResolveCtx()
    @Shared TypeMirror optionalOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
    @Shared TypeMirror setOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
    @Shared TypeMirror stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
    @Shared TypeMirror streamOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)

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

    def 'streamElement yields the element type for every stream-shaped container'() {
        expect:
        ctx.types().isSameType(Containers.streamElement(stringArray, ctx).get(), TypeUniverse.STRING)
        ctx.types().isSameType(Containers.streamElement(TypeUniverse.LIST_OF_STRING, ctx).get(), TypeUniverse.STRING)
        ctx.types().isSameType(Containers.streamElement(setOfString, ctx).get(), TypeUniverse.STRING)
        ctx.types().isSameType(Containers.streamElement(optionalOfString, ctx).get(), TypeUniverse.STRING)
        ctx.types().isSameType(Containers.streamElement(streamOfString, ctx).get(), TypeUniverse.STRING)
    }

    def 'streamElement is empty for a non-stream-shaped type'() {
        expect:
        Containers.streamElement(TypeUniverse.STRING, ctx).empty
        Containers.streamElement(TypeUniverse.INT, ctx).empty
    }

    def 'streamOf forms Stream<element> for a reference element and is empty for a primitive'() {
        when:
        def streamOfString = Containers.streamOf(TypeUniverse.STRING, ctx)

        then:
        streamOfString.present
        Containers.isStream(streamOfString.get(), ctx)
        ctx.types().isSameType(Containers.typeArgument(streamOfString.get(), 0), TypeUniverse.STRING)

        and: 'an array element is a reference type too'
        Containers.streamOf(stringArray, ctx).present

        and: 'a primitive element forms no Stream'
        Containers.streamOf(TypeUniverse.INT, ctx).empty
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
}
