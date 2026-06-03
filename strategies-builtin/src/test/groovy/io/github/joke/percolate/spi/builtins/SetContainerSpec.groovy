package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.ElementScope
import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.builtins.test.Renders
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

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set'].each { TypeUniverse.elements().getTypeElement(it) }
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        Containers.isSet(setOfString, ctx)
    }

    def 'Set<E> target emits EXITING collect + scopeless wrap'() {
        when:
        def steps = new SetContainer().bridge(TypeUniverse.STRING, setOfString, ctx).toList()

        then:
        steps.size() == 2
        def collect = steps.find { it.scope.orElse(null) == ElementScope.EXITING }
        def wrap = steps.find { it.intent == Intent.BOUNDARY && it.scope.empty }
        collect.codegen instanceof ContainerCodegen
        Renders.edge(wrap.codegen, 'x') == 'java.util.Set.of(x)'
    }

    def 'declines when target is not a Set'() {
        expect:
        new SetContainer().bridge(TypeUniverse.STRING, TypeUniverse.LIST_OF_STRING, ctx).toList().empty
    }

    def 'collect renders toSet'() {
        expect:
        new SetContainer().collect(CodeBlock.of('s')).toString() == 's.collect(java.util.stream.Collectors.toSet())'
    }
}
