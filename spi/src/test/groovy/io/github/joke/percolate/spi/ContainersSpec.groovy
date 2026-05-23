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
        javax.lang.model.element.TypeElement mapperType() {
            null
        }

        @Override
        javax.lang.model.element.ExecutableElement currentMethod() {
            null
        }

        @Override
        CallableMethods callableMethods() {
            null
        }
    }
}
