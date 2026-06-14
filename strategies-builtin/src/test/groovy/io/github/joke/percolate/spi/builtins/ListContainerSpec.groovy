package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
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
    @Shared TypeMirror setOfString
    @Shared TypeMirror streamOfString

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        listOfString = TypeUniverse.LIST_OF_STRING
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
        Containers.isList(listOfString, ctx)
    }

    def 'iterates a List into a Stream via .stream(), a plain operation with no child scope'() {
        when:
        def specs = new ListContainer().bridge(listOfString, Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        ctx.types().isSameType(iterate.ports[0].type, listOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new ListContainer().iterate(CodeBlock.of('$N', 'xs')).toString().contains('.stream()')
    }

    def 'collects a Stream into a List and offers a plain single-element List.of wrap'() {
        when:
        def specs = new ListContainer().bridge(streamOfString, Demands.forTarget(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { ctx.types().isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.types().isSameType(collect.outputType, listOfString)
        new ListContainer().collect(CodeBlock.of('$N', 's')).toString().contains('toList()')

        and: 'a plain single-element wrap String -> List<String>, no child scope'
        def wrap = specs.find { ctx.types().isSameType(it.ports[0].type, TypeUniverse.STRING) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.outputType, listOfString)
        wrap.outputNullness == Nullability.NON_NULL

        and: 'no operation carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'declines when neither side is a List'() {
        expect:
        new ListContainer().bridge(TypeUniverse.STRING, Demands.forTarget(setOfString), ctx).toList().empty
    }
}
