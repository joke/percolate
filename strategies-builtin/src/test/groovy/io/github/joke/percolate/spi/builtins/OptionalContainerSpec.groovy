package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link OptionalContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): every seam question is stubbed on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class OptionalContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement optionalElement = Mock()
    TypeElement streamElement = Mock()
    TypeMirror optionalOfString = Mock()
    TypeMirror streamOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror optionalRawType = Mock()
    TypeMirror streamRawType = Mock()

    def 'an Optional<E> target offers a plain ofNullable wrap from a scalar, no child scope'() {
        ctx.isOptional(optionalOfString) >> true
        ctx.typeArgument(optionalOfString, 0) >> stringType
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        optionalElement.asType() >> optionalRawType

        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(optionalOfString), ctx).toList()

        then:
        def wrap = specs.find { it.childScope.empty && it.ports[0].type.is(stringType) }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        wrap.outputType.is(optionalOfString)
        wrap.outputNullness == Nullability.NON_NULL
        new OptionalContainer().wrap().get().render(CodeBlock.of('$N', 'x')).toString().contains('ofNullable')
    }

    def 'an Optional<E> target offers a same-kind functor-lift mapPresence over a type-variable Optional<A> port'() {
        ctx.isOptional(optionalOfString) >> true
        ctx.typeArgument(optionalOfString, 0) >> stringType
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        optionalElement.asType() >> optionalRawType

        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(optionalOfString), ctx).toList()

        then: 'a scope-owning mapPresence Optional<A> -> Optional<String>, child A -> String'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        mapping.ports[0].template == PortType.app(optionalElement, [PortType.variable(0)])
        def child = mapping.childScope.get()
        child.elementInTemplate == PortType.variable(0)
        child.elementOut.is(stringType)
        mapping.outputType.is(optionalOfString)
        !mapping.partial
        new OptionalContainer().mapPresence().get().weave(CodeBlock.of('$N', 'o'), 'v', CodeBlock.of('$N', 'b'))
                .toString().contains('.map(')
    }

    def 'an Optional<E> iterates to a 0-or-1 Stream via Optional.stream(), a plain operation'() {
        ctx.isOptional(streamOfString) >> false
        ctx.isDeclared(streamOfString) >> true
        ctx.erasure(streamOfString) >> streamOfString
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        streamElement.asType() >> streamRawType
        ctx.erasure(streamRawType) >> streamRawType
        ctx.isSameType(streamOfString, streamRawType) >> true
        ctx.typeArgument(streamOfString, 0) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then: 'the iterate produces Stream<String> from Optional<String>'
        def iterate = specs.find { it.ports[0].type.is(optionalOfString) }
        iterate != null
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.outputType.is(streamOfString)
        new OptionalContainer().iterate().get().render(CodeBlock.of('$N', 'o')).toString().contains('.stream()')
    }

    def 'a scalar target offers a plain partial unwrap from Optional<scalar> under the demanded nullness'() {
        ctx.isOptional(stringType) >> false
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        ctx.declaredType(optionalElement, stringType) >> optionalOfString

        when:
        def demand = Demands.forTarget(stringType, Nullability.NULLABLE)
        def specs = new OptionalContainer().expand(demand, ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        unwrap.ports[0].type.is(optionalOfString)
        unwrap.outputType.is(stringType)
        unwrap.outputNullness == Nullability.NULLABLE
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NULLABLE).toString().contains('orElse(null)')
        new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NON_NULL).toString().contains('orElseThrow')
    }
}
