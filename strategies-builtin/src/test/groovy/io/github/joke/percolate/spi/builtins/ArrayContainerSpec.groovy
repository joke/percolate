package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
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
class ArrayContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror stringArray
    @Shared TypeMirror streamOfString

    def setupSpec() {
        stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
    }

    def 'iterates an array into a Stream via Arrays.stream, a plain operation with no child scope'() {
        when:
        def specs = new ArrayContainer().bridge(stringArray, Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        ctx.types().isSameType(iterate.ports[0].type, stringArray)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new ArrayContainer().iterate(CodeBlock.of('$N', 'a')).toString().contains('Arrays.stream')
    }

    def 'collects a Stream into an array and offers no single-element wrap (arrays have no factory)'() {
        when:
        def specs = new ArrayContainer().bridge(streamOfString, Demands.forTarget(stringArray), ctx).toList()

        then:
        specs.size() == 1
        def collect = specs[0]
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.types().isSameType(collect.ports[0].type, streamOfString)
        ctx.types().isSameType(collect.outputType, stringArray)
        new ArrayContainer().collect(CodeBlock.of('$N', 's')).toString().contains('toArray()')
    }

    def 'declines when neither side is an array'() {
        expect:
        new ArrayContainer().bridge(TypeUniverse.STRING, Demands.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }
}
