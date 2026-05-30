package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeTransition
import io.github.joke.percolate.spi.VarNames
import io.github.joke.percolate.spi.Weights
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

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        listOfString = TypeUniverse.LIST_OF_STRING
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        Containers.isList(listOfString, ctx)
    }

    def 'List<E> target emits an EXITING collect (provider) and a PRESERVING single-element wrap (EdgeCodegen)'() {
        when:
        def steps = new ListContainer().bridge(TypeUniverse.STRING, listOfString, ctx).toList()

        then:
        steps.size() == 2
        def collect = steps.find { it.scopeTransition == ScopeTransition.EXITING }
        def wrap = steps.find { it.scopeTransition == ScopeTransition.PRESERVING }
        collect != null && wrap != null
        ctx.types().isSameType(collect.inputType, TypeUniverse.STRING)
        ctx.types().isSameType(collect.outputType, listOfString)
        collect.weight == Weights.CONTAINER
        collect.codegen instanceof ContainerCodegen
        wrap.codegen instanceof EdgeCodegen
        renderEdge(wrap.codegen, 'x') == 'java.util.List.of(x)'
    }

    def 'List<E> source emits an ENTERING iterate carrying the provider'() {
        when:
        def steps = new ListContainer().bridge(listOfString, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].scopeTransition == ScopeTransition.ENTERING
        ctx.types().isSameType(steps[0].inputType, listOfString)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.STRING)
        steps[0].codegen instanceof ContainerCodegen
    }

    def 'declines when neither side is a List'() {
        expect:
        new ListContainer().bridge(TypeUniverse.STRING, setOfString, ctx).toList().empty
    }

    def 'stream snippets render the List paradigm'() {
        given:
        def c = new ListContainer()

        expect:
        c.iterate(CodeBlock.of('xs')).toString() == 'xs.stream()'
        c.mapElements(CodeBlock.of('s'), 'e', CodeBlock.of('f(e)')).toString() == 's.map(e -> f(e))'
        c.flatMapElements(CodeBlock.of('s'), 'e', CodeBlock.of('e.stream()')).toString() == 's.flatMap(e -> e.stream())'
        c.collect(CodeBlock.of('s')).toString() == 's.collect(java.util.stream.Collectors.toList())'
    }

    private static String renderEdge(final EdgeCodegen codegen, final String inputName) {
        codegen.render(new VarNames() {}, new IncomingValues() {
            CodeBlock single() { CodeBlock.of(inputName) }
            CodeBlock byGroupPosition(final int idx) { CodeBlock.of(inputName) }
            CodeBlock byName(final String slotName) { CodeBlock.of(inputName) }
        }).toString()
    }
}
