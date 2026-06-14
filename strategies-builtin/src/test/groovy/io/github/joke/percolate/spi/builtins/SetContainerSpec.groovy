package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
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
    @Shared TypeMirror streamOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
        Containers.isSet(setOfString, ctx)
    }

    def 'iterates a Set into a Stream via .stream(), a plain operation with no child scope'() {
        when:
        def specs = new SetContainer().bridge(setOfString, Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.types().isSameType(iterate.ports[0].type, setOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new SetContainer().iterate(CodeBlock.of('$N', 'xs')).toString().contains('.stream()')
    }

    def 'collects a Stream into a Set (Collectors.toSet) and offers a plain single-element Set.of wrap'() {
        when:
        def specs = new SetContainer().bridge(streamOfString, Demands.forTarget(setOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> Set<String>'
        def collect = specs.find { ctx.types().isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        collect.weight == Weights.CONTAINER
        ctx.types().isSameType(collect.outputType, setOfString)
        new SetContainer().collect(CodeBlock.of('$N', 's')).toString().contains('toSet()')

        and: 'a plain single-element wrap String -> Set<String>'
        def wrap = specs.find { ctx.types().isSameType(it.ports[0].type, TypeUniverse.STRING) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.outputType, setOfString)
    }

    def 'declines when neither side is a Set'() {
        expect:
        new SetContainer().bridge(TypeUniverse.STRING, Demands.forTarget(TypeUniverse.LIST_OF_STRING), ctx).toList().empty
    }
}
