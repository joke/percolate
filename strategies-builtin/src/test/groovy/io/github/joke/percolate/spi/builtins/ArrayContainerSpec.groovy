package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ArrayContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror stringArray
    @Shared TypeMirror integerArray

    def setupSpec() {
        stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
        integerArray = TypeUniverse.types().getArrayType(TypeUniverse.INTEGER)
    }

    def 'A[] to B[] emits a scope-owning element mapping declaring element types A and B'() {
        when:
        def specs = new ArrayContainer().bridge(integerArray, Demands.forTarget(stringArray), ctx).toList()

        then:
        specs.size() == 1
        def mapping = specs[0]
        mapping.childScope.present
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        ctx.types().isSameType(mapping.ports[0].type, integerArray)
        ctx.types().isSameType(mapping.outputType, stringArray)
        mapping.outputNullness == Nullability.NON_NULL
        mapping.weight == Weights.CONTAINER
        mapping.codegen instanceof ContainerCodegen
    }

    def 'array target with a scalar source emits nothing (no synchronous single-element wrap)'() {
        expect:
        new ArrayContainer().bridge(TypeUniverse.STRING, Demands.forTarget(stringArray), ctx).toList().empty
    }

    def 'declines when the target is not an array'() {
        expect:
        new ArrayContainer().bridge(TypeUniverse.STRING, Demands.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }
}
