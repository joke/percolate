package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeTransition
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ListCollectSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror listOfString
    @Shared TypeMirror setOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each {
            TypeUniverse.elements().getTypeElement(it)
        }
        listOfString = TypeUniverse.LIST_OF_STRING
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        Containers.isList(listOfString, ctx)
    }

    def 'emits step for List<E> target with element source'() {
        when:
        def steps = new ListCollect().bridge(TypeUniverse.STRING, listOfString, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, listOfString)
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.EXITING
        steps[0].elementRole == 'element'
    }

    def 'declines when target is not List'() {
        when:
        def steps = new ListCollect().bridge(TypeUniverse.STRING, setOfString, ctx).toList()

        then:
        steps.empty
    }

    def 'emits regardless of source type (EXITING allocates fresh element-scope input)'() {
        when:
        def steps = new ListCollect().bridge(TypeUniverse.INTEGER, listOfString, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, listOfString)
        steps[0].scopeTransition == ScopeTransition.EXITING
    }
}
