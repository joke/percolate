package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.Containers
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
class SetContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror setOfString
    @Shared TypeMirror setOfInteger

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        setOfInteger = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.INTEGER)
        Containers.isSet(setOfString, ctx)
    }

    def 'Set<A> to Set<B> emits a scope-owning element mapping declaring element types A and B'() {
        when:
        def specs = new SetContainer().bridge(setOfInteger, Demands.forTarget(setOfString), ctx).toList()

        then:
        def mapping = specs.find { it.childScope.present }
        mapping != null
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        mapping.weight == Weights.CONTAINER
        mapping.codegen instanceof ContainerCodegen
    }

    def 'Set<E> target with a scalar source emits a plain single-element wrap with no child scope'() {
        when:
        def specs = new SetContainer().bridge(TypeUniverse.STRING, Demands.forTarget(setOfString), ctx).toList()

        then:
        specs.size() == 1
        def wrap = specs[0]
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(wrap.outputType, setOfString)
    }

    def 'declines when the target is not a Set'() {
        expect:
        new SetContainer().bridge(TypeUniverse.STRING, Demands.forTarget(TypeUniverse.LIST_OF_STRING), ctx).toList().empty
    }
}
