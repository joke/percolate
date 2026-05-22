package io.github.joke.percolate.spi.builtins

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
class ArrayCollectSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror stringArray

    def setupSpec() {
        stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
    }

    def 'emits step for array target with element source'() {
        when:
        def steps = new ArrayCollect().bridge(TypeUniverse.STRING, stringArray, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, stringArray)
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.EXITING
        steps[0].elementRole == 'element'
    }

    def 'declines when target is not an array'() {
        when:
        def steps = new ArrayCollect().bridge(TypeUniverse.STRING, TypeUniverse.LIST_OF_STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'emits regardless of source type (EXITING allocates fresh element-scope input)'() {
        when:
        def steps = new ArrayCollect().bridge(TypeUniverse.INTEGER, stringArray, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, stringArray)
        steps[0].scopeTransition == ScopeTransition.EXITING
    }
}
