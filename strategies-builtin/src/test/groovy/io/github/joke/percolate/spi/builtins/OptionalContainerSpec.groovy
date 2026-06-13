package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.WrapperCodegen
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

    def setupSpec() {
        def optional = TypeUniverse.elements().getTypeElement('java.util.Optional')
        optionalOfString = TypeUniverse.types().getDeclaredType(optional, TypeUniverse.STRING)
        optionalOfInteger = TypeUniverse.types().getDeclaredType(optional, TypeUniverse.INTEGER)
        Containers.isOptional(optionalOfString, ctx)
    }

    def 'Optional<E> target emits a plain wrap with no child scope (the wrap declares none)'() {
        when:
        def specs = new OptionalContainer().bridge(TypeUniverse.STRING, Demands.forTarget(optionalOfString), ctx).toList()

        then:
        specs.size() == 1
        def wrap = specs[0]
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        wrap.ports.size() == 1
        ctx.types().isSameType(wrap.ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(wrap.outputType, optionalOfString)
        wrap.outputNullness == Nullability.NON_NULL
    }

    def 'Optional<A> to Optional<B> emits a scope-owning element mapping declaring element types A and B'() {
        when:
        def specs = new OptionalContainer().bridge(optionalOfInteger, Demands.forTarget(optionalOfString), ctx).toList()

        then:
        def mapping = specs.find { it.childScope.present }
        mapping != null
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        ctx.types().isSameType(mapping.ports[0].type, optionalOfInteger)
        mapping.weight == Weights.CONTAINER
        mapping.codegen instanceof WrapperCodegen
    }

    def 'scalar target synthesises a plain unwrap from Optional<target>, collapsing under the demanded nullness'() {
        when:
        // The from side is ignored: an unwrap is offered for any scalar target by synthesising Optional<target>
        // as its input port, so a wrapped source can be reached even before a wrapper Value exists.
        def demand = Demands.forTarget(TypeUniverse.STRING, [TypeUniverse.INTEGER], Nullability.NULLABLE)
        def specs = new OptionalContainer().bridge(TypeUniverse.INTEGER, demand, ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof WrapperCodegen
        unwrap.weight == Weights.CONTAINER
        ctx.types().isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.types().isSameType(unwrap.outputType, TypeUniverse.STRING)
        unwrap.outputNullness == Nullability.NULLABLE
    }
}
