package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * The generic, kind-free element transform over {@code Stream<T>} (design D7). It names no container kind: it
 * reads its source element from {@link Containers#streamElement} of any candidate and offers a scope-owning
 * {@code map} ({@code child A -> B}) and {@code flatMap} ({@code child A -> Stream<B>}). Cross-kind and flatten
 * emerge from composing this with each container's own {@code iterate}/{@code collect}.
 */
@Tag('unit')
class StreamMapSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror listOfInteger
    @Shared TypeMirror listOfString
    @Shared TypeMirror streamOfString
    @Shared TypeMirror streamOfInteger

    def setupSpec() {
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection', 'java.util.List'].each {
            TypeUniverse.elements().getTypeElement(it)
        }
        listOfInteger = TypeUniverse.LIST_OF_INT
        listOfString = TypeUniverse.LIST_OF_STRING
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
        streamOfInteger = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.INTEGER)
    }

    def 'a Stream<B> demand from a candidate with stream-element A emits scope-owning map and flatMap'() {
        when:
        def specs = new StreamMap().bridge(listOfInteger, Demands.forTarget(streamOfString), ctx).toList()

        then: 'a map: child Integer -> String'
        def map = specs.find {
            it.childScope.present && ctx.types().isSameType(it.childScope.get().elementOut, TypeUniverse.STRING)
        }
        map != null
        map.codegen instanceof ScopeCodegen
        map.weight == Weights.CONTAINER
        ctx.types().isSameType(map.ports[0].type, streamOfInteger)
        ctx.types().isSameType(map.outputType, streamOfString)
        ctx.types().isSameType(map.childScope.get().elementIn, TypeUniverse.INTEGER)
        ((ScopeCodegen) map.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b')).toString().contains('.map(')

        and: 'a flatMap: child Integer -> Stream<String>'
        def flatMap = specs.find {
            it.childScope.present && ctx.types().isSameType(it.childScope.get().elementOut, streamOfString)
        }
        flatMap != null
        flatMap.codegen instanceof ScopeCodegen
        ctx.types().isSameType(flatMap.ports[0].type, streamOfInteger)
        ctx.types().isSameType(flatMap.outputType, streamOfString)
        ctx.types().isSameType(flatMap.childScope.get().elementIn, TypeUniverse.INTEGER)
        ((ScopeCodegen) flatMap.codegen).weave(CodeBlock.of('$N', 's'), 'v', CodeBlock.of('$N', 'b')).toString().contains('.flatMap(')
    }

    def 'it names no container kind — it matches on the candidate stream-element, not the container type'() {
        expect: 'an array candidate works exactly like a List candidate (stream-element is structural)'
        def arrayOfInteger = TypeUniverse.types().getArrayType(TypeUniverse.INTEGER)
        !new StreamMap().bridge(arrayOfInteger, Demands.forTarget(streamOfString), ctx).toList().empty
    }

    def 'declines the degenerate self-map where the source stream already equals the demand'() {
        expect: 'List<String> already iterates straight to Stream<String>; no Stream<String> -> Stream<String> identity'
        new StreamMap().bridge(listOfString, Demands.forTarget(streamOfString), ctx).toList().empty
    }

    def 'declines when the target is not a Stream'() {
        expect:
        new StreamMap().bridge(listOfInteger, Demands.forTarget(listOfString), ctx).toList().empty
    }

    def 'declines when the candidate has no stream element'() {
        expect:
        new StreamMap().bridge(TypeUniverse.STRING, Demands.forTarget(streamOfString), ctx).toList().empty
    }
}
