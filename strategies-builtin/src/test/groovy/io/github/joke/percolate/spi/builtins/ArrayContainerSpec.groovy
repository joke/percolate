package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.ElementScope
import io.github.joke.percolate.spi.ResolveCtx
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

    def setupSpec() {
        stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
    }

    def 'array target emits only an EXITING collect (no single-element wrap)'() {
        when:
        def steps = new ArrayContainer().bridge(TypeUniverse.STRING, stringArray, ctx).toList()

        then:
        steps.size() == 1
        steps[0].scope.orElse(null) == ElementScope.EXITING
        steps[0].codegen instanceof ContainerCodegen
        ctx.types().isSameType(steps[0].inputs[0].type, TypeUniverse.STRING)
    }

    def 'array source emits an ENTERING iterate'() {
        when:
        def steps = new ArrayContainer().bridge(stringArray, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].scope.orElse(null) == ElementScope.ENTERING
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
    }

    def 'iterate / collect render the array paradigm'() {
        given:
        def c = new ArrayContainer()

        expect:
        c.iterate(CodeBlock.of('xs')).toString() == 'java.util.Arrays.stream(xs)'
        c.collect(CodeBlock.of('s')).toString() == 's.toArray()'
    }
}
