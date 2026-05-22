package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
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
class IterableUnwrapSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror listOfString
    @Shared TypeMirror setOfString
    @Shared TypeMirror optionalOfString
    @Shared TypeMirror stringArray

    def setupSpec() {
        // Pre-warm javac symbol table to avoid re-entrant class-completion assertions on JDK 21+
        // (Iterable hierarchy walks through SequencedCollection on JDK 21+).
        ['java.lang.Iterable', 'java.util.Collection', 'java.util.SequencedCollection',
         'java.util.List', 'java.util.Set', 'java.util.Optional'].each {
            TypeUniverse.elements().getTypeElement(it)
        }
        listOfString = TypeUniverse.LIST_OF_STRING
        setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        optionalOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
        stringArray = TypeUniverse.types().getArrayType(TypeUniverse.STRING)
        Containers.isIterable(listOfString, ctx)
        Containers.isIterable(setOfString, ctx)
    }

    def 'emits step for List<E> to E with ENTERING transition and element role'() {
        when:
        def steps = new IterableUnwrap().bridge(listOfString, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, listOfString)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.STRING)
        steps[0].weight == Weights.CONTAINER
        steps[0].scopeTransition == ScopeTransition.ENTERING
        steps[0].elementRole == 'element'
        renderCodegen(steps[0].codegen, 'x').toString() == 'x'
    }

    def 'emits step for Set<E> to E'() {
        when:
        def steps = new IterableUnwrap().bridge(setOfString, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, setOfString)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.STRING)
        steps[0].scopeTransition == ScopeTransition.ENTERING
    }

    def 'emits step for array source to element type'() {
        when:
        def steps = new IterableUnwrap().bridge(stringArray, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        ctx.types().isSameType(steps[0].inputType, stringArray)
        ctx.types().isSameType(steps[0].outputType, TypeUniverse.STRING)
        steps[0].scopeTransition == ScopeTransition.ENTERING
        steps[0].elementRole == 'element'
    }

    def 'declines Optional source (handled by OptionalUnwrap)'() {
        when:
        def steps = new IterableUnwrap().bridge(optionalOfString, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'declines non-iterable source'() {
        when:
        def steps = new IterableUnwrap().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'declines when target type is not the element type'() {
        when:
        def steps = new IterableUnwrap().bridge(listOfString, TypeUniverse.INTEGER, ctx).toList()

        then:
        steps.empty
    }

    private static CodeBlock renderCodegen(final codegen, final String inputName) {
        codegen.render(new VarNames() {}, new IncomingValues() {
            @Override
            CodeBlock single() { CodeBlock.of(inputName) }

            @Override
            CodeBlock byGroupPosition(final int idx) { CodeBlock.of(inputName) }

            @Override
            CodeBlock byName(final String slotName) { CodeBlock.of(inputName) }
        })
    }
}
