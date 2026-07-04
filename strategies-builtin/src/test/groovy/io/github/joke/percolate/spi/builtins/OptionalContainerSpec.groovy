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
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import io.github.joke.percolate.spi.types.TypeRef
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * The {@code Optional} presence container, target-driven (design D1/D8): keyed on the demanded target alone (no
 * candidate). When the target is {@code Optional<E>} it offers a plain {@code ofNullable} wrap and a same-kind
 * {@code mapPresence} <b>functor lift</b> (over a type-variable {@code Optional<A>} port the engine grounds); when the
 * target is {@code Stream<E>} it offers a 0-or-1 {@code iterate} from {@code Optional<E>}; for a scalar target it
 * offers a partial {@code unwrap} from {@code Optional<scalar>}. Over-emitted producers are pruned by the driver when
 * no matching source exists.
 */
@Tag('unit')
class OptionalContainerSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared ResolveCtx ctx = new ResolveCtxBuilder(javac).build()
    @Shared TypeMirror optionalOfString
    @Shared TypeMirror streamOfString

    def setupSpec() {
        def optional = javac.elements().getTypeElement('java.util.Optional')
        optionalOfString = javac.types().getDeclaredType(optional, javac.STRING)
        streamOfString = javac.types().getDeclaredType(
                javac.elements().getTypeElement('java.util.stream.Stream'), javac.STRING)
        Containers.isOptional(optionalOfString, ctx)
    }

    def 'an Optional<E> target offers a plain ofNullable wrap from a scalar, no child scope'() {
        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(optionalOfString), ctx).toList()

        then:
        def wrap = specs.find { it.childScope.empty && ctx.types().isSameType(it.ports[0].type, javac.STRING) }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        ctx.types().isSameType(wrap.outputType, optionalOfString)
        wrap.outputNullness == Nullability.NON_NULL
        new OptionalContainer().wrap().get().render(CodeBlock.of('$N', 'x')).toString().contains('ofNullable')
    }

    def 'an Optional<E> target offers a same-kind functor-lift mapPresence over a type-variable Optional<A> port'() {
        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(optionalOfString), ctx).toList()

        then: 'a scope-owning mapPresence Optional<A> -> Optional<String>, child A -> String'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        mapping.ports[0].template == TypeRef.declared('java.util.Optional', TypeRef.variable('V0'))
        def child = mapping.childScope.get()
        child.elementInTemplate == TypeRef.variable('V0')
        ctx.types().isSameType(child.elementOut, javac.STRING)
        ctx.types().isSameType(mapping.outputType, optionalOfString)
        !mapping.partial
        new OptionalContainer().mapPresence().get().weave(CodeBlock.of('$N', 'o'), 'v', CodeBlock.of('$N', 'b'))
                .toString().contains('.map(')
    }

    def 'an Optional<E> iterates to a 0-or-1 Stream via Optional.stream(), a plain operation'() {
        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'the iterate produces Stream<String> from Optional<String>'
        def iterate = specs.find { ctx.types().isSameType(it.ports[0].type, optionalOfString) }
        iterate != null
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.types().isSameType(iterate.outputType, streamOfString)
        new OptionalContainer().iterate().get().render(CodeBlock.of('$N', 'o')).toString().contains('.stream()')
    }

    def 'a scalar target offers a plain partial unwrap from Optional<scalar> under the demanded nullness'() {
        when:
        def demand = Demands.forTarget(javac.STRING, Nullability.NULLABLE)
        def specs = new OptionalContainer().expand(demand, ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        ctx.types().isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.types().isSameType(unwrap.outputType, javac.STRING)
        unwrap.outputNullness == Nullability.NULLABLE
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NULLABLE).toString().contains('orElse(null)')
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NON_NULL).toString().contains('orElseThrow')
    }
}
