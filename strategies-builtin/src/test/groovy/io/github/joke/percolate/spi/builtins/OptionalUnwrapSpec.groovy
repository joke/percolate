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
class OptionalUnwrapSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror optionalOfString

    def setupSpec() {
        optionalOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
    }

    def 'emits step producing T from Optional<T> with ENTERING and element role'() {
        when:
        def steps = new OptionalUnwrap().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, optionalOfString)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.STRING)
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.ENTERING
        steps[0].elementRole == 'element'
    }

    def 'declines when target is itself an Optional (no recursive unwrap)'() {
        when:
        def steps = new OptionalUnwrap().bridge(TypeUniverse.STRING, optionalOfString, ctx).toList()

        then:
        steps.empty
    }
}
