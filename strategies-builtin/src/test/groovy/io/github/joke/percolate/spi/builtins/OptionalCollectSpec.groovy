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
class OptionalCollectSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror optionalOfString
    @Shared TypeMirror listOfString

    def setupSpec() {
        optionalOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
        listOfString = TypeUniverse.LIST_OF_STRING
    }

    def 'emits step for Optional<E> target with element source'() {
        when:
        def steps = new OptionalCollect().bridge(TypeUniverse.STRING, optionalOfString, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, optionalOfString)
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.EXITING
        steps[0].elementRole == 'element'
    }

    def 'declines when target is not Optional'() {
        when:
        def steps = new OptionalCollect().bridge(TypeUniverse.STRING, listOfString, ctx).toList()

        then:
        steps.empty
    }

    def 'emits regardless of source type (EXITING allocates fresh element-scope input)'() {
        when:
        def steps = new OptionalCollect().bridge(TypeUniverse.INTEGER, optionalOfString, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, TypeUniverse.STRING)
        ctx.types().isSameType(steps[0].outputType, optionalOfString)
        steps[0].scopeTransition == ScopeTransition.EXITING
    }
}
