package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ElementScope
import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Renders
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

    def 'List<E> target emits an EXITING collect (provider) and a scopeless single-element wrap (EdgeCodegen)'() {
        when:
        def steps = new ListContainer().bridge(TypeUniverse.STRING, listOfString, ctx).toList()

        then:
        steps.size() == 2
        def collect = steps.find { it.scope.orElse(null) == ElementScope.EXITING }
        def wrap = steps.find { it.intent == Intent.BOUNDARY && it.scope.empty }
        collect != null && wrap != null
        ctx.types().isSameType(collect.inputs[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(collect.output, listOfString)
        collect.weight == Weights.CONTAINER
        collect.codegen instanceof ContainerCodegen
        wrap.codegen instanceof EdgeCodegen
        Renders.edge(wrap.codegen, 'x') == 'java.util.List.of(x)'
    }

    def 'List<E> source emits an ENTERING iterate carrying the provider'() {
        when:
        def steps = new ListContainer().bridge(listOfString, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].scope.orElse(null) == ElementScope.ENTERING
        ctx.types().isSameType(steps[0].inputs[0].type, listOfString)
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
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
}
