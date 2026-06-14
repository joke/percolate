package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
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

@Tag('unit')
class OptionalContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror optionalOfString
    @Shared TypeMirror optionalOfInteger
    @Shared TypeMirror streamOfString

    def setupSpec() {
        def optional = TypeUniverse.elements().getTypeElement('java.util.Optional')
        optionalOfString = TypeUniverse.types().getDeclaredType(optional, TypeUniverse.STRING)
        optionalOfInteger = TypeUniverse.types().getDeclaredType(optional, TypeUniverse.INTEGER)
        streamOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)
        Containers.isOptional(optionalOfString, ctx)
    }

    def 'Optional<E> target from a scalar emits a plain ofNullable wrap with no child scope'() {
        when:
        def specs = new OptionalContainer().bridge(TypeUniverse.STRING, Demands.forTarget(optionalOfString), ctx).toList()

        then:
        specs.size() == 1
        def wrap = specs[0]
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        ctx.types().isSameType(wrap.ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(wrap.outputType, optionalOfString)
        wrap.outputNullness == Nullability.NON_NULL
        new OptionalContainer().wrap().get().render(CodeBlock.of('$N', 'x')).toString().contains('ofNullable')
    }

    def 'Optional<A> to Optional<B> emits the plain wrap plus a same-kind scope-owning mapPresence'() {
        when:
        def specs = new OptionalContainer().bridge(optionalOfInteger, Demands.forTarget(optionalOfString), ctx).toList()

        then: 'a scope-owning mapPresence Optional<Integer> -> Optional<String>'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        ctx.types().isSameType(mapping.ports[0].type, optionalOfInteger)
        ctx.types().isSameType(mapping.outputType, optionalOfString)
        !mapping.partial
        new OptionalContainer().mapPresence().get().weave(CodeBlock.of('$N', 'o'), 'v', CodeBlock.of('$N', 'b'))
                .toString().contains('.map(')

        and: 'the plain wrap is offered too (no collect — a wrapper has no sequence terminal)'
        specs.any { it.childScope.empty && ctx.types().isSameType(it.ports[0].type, TypeUniverse.STRING) }
    }

    def 'Optional<E> iterates to a 0-or-1 Stream via Optional.stream(), a plain operation'() {
        when:
        def specs = new OptionalContainer().bridge(optionalOfString, Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.types().isSameType(iterate.ports[0].type, optionalOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new OptionalContainer().iterate().get().render(CodeBlock.of('$N', 'o')).toString().contains('.stream()')
    }

    def 'a scalar demand from an Optional source emits a plain partial unwrap under the demanded nullness'() {
        when:
        def demand = Demands.forTarget(TypeUniverse.STRING, [], Nullability.NULLABLE)
        def specs = new OptionalContainer().bridge(optionalOfString, demand, ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        ctx.types().isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.types().isSameType(unwrap.outputType, TypeUniverse.STRING)
        unwrap.outputNullness == Nullability.NULLABLE
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NULLABLE).toString().contains('orElse(null)')
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NON_NULL).toString().contains('orElseThrow')
    }
}
