package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
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
class ListContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror listOfString
    @Shared TypeMirror listOfInteger
    @Shared TypeMirror setOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        listOfString = TypeUniverse.LIST_OF_STRING
        listOfInteger = TypeUniverse.LIST_OF_INT
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        Containers.isList(listOfString, ctx)
    }

    def 'List<A> to List<B> emits a scope-owning element mapping declaring element types A and B'() {
        when:
        def specs = new ListContainer().bridge(listOfInteger, Demands.forTarget(listOfString), ctx).toList()

        then:
        def mapping = specs.find { it.childScope.present }
        mapping != null
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        child.elementInNullness == Nullability.NON_NULL
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        child.elementOutNullness == Nullability.NON_NULL
        mapping.ports.size() == 1
        ctx.types().isSameType(mapping.ports[0].type, listOfInteger)
        ctx.types().isSameType(mapping.outputType, listOfString)
        mapping.outputNullness == Nullability.NON_NULL
        mapping.weight == Weights.CONTAINER
        mapping.codegen instanceof ContainerCodegen
    }

    def 'List<E> target with a scalar source emits a plain single-element wrap with no child scope'() {
        when:
        def specs = new ListContainer().bridge(TypeUniverse.STRING, Demands.forTarget(listOfString), ctx).toList()

        then:
        specs.size() == 1
        def wrap = specs[0]
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        wrap.ports.size() == 1
        ctx.types().isSameType(wrap.ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(wrap.outputType, listOfString)
    }

    def 'declines when the target is not a List'() {
        expect:
        new ListContainer().bridge(TypeUniverse.STRING, Demands.forTarget(setOfString), ctx).toList().empty
    }
}
