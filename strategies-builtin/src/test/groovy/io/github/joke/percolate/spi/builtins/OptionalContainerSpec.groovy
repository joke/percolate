package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.javapoet.CodeBlock
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

    def 'an Optional<E> target offers a plain ofNullable wrap from a scalar, no child scope, a NULLABLE element port'() {
        ctx.isOptional(optionalOfString) >> true
        ctx.typeArgument(optionalOfString, 0) >> stringType
        ctx.typeElementNamed('java.util.Optional') >> optionalElement
        optionalElement.asType() >> optionalRawType

        when:
        def specs = new OptionalContainer().expand(Demands.forTarget(optionalOfString), ctx).toList()

        then: 'the element port is NULLABLE — ofNullable is null-safe, so a @Nullable source binds directly'
        def wrap = specs.find { it.childScope.empty && it.ports[0].type.is(stringType) }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        wrap.weight == Weights.CONTAINER
        wrap.outputType.is(optionalOfString)
        wrap.outputNullness == Nullability.NON_NULL
        wrap.ports[0].nullness == Nullability.NULLABLE
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
        CodeBlock.of('$L\n', new OptionalContainer().mapPresence().get().weave(CodeBlock.of('$N', 'o'), 'v', CodeBlock.of('$N', 'b')))
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
        CodeBlock.of('$L\n', new OptionalContainer().iterate().get().render(CodeBlock.of('$N', 'o'))).toString().contains('.stream()')
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
        CodeBlock.of('$L\n', new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NULLABLE)).toString().contains('orElse(null)')
        CodeBlock.of('$L\n', new OptionalContainer().unwrap().get().render(CodeBlock.of('$N', 'o'), Nullability.NON_NULL)).toString().contains('orElseThrow')
    }

    def 'matches delegates the optional-kind question to the seam'() {
        ctx.isOptional(optionalOfString) >> true

        expect:
        new OptionalContainer().matches(optionalOfString, ctx)
    }

    def 'a non-optional target does not match'() {
        ctx.isOptional(optionalOfString) >> false

        expect:
        !new OptionalContainer().matches(optionalOfString, ctx)
    }

    def 'element extracts the sole type argument via the seam'() {
        ctx.typeArgument(optionalOfString, 0) >> stringType

        expect:
        new OptionalContainer().element(optionalOfString, ctx).is(stringType)
    }

    def 'kindErasure resolves java.util.Optional through the seam'() {
        ctx.typeElementNamed('java.util.Optional') >> optionalElement

        expect:
        new OptionalContainer().kindErasure(ctx).get().is(optionalElement)
    }

    def 'kindErasure is empty when java.util.Optional is not resolvable'() {
        ctx.typeElementNamed('java.util.Optional') >> null

        expect:
        new OptionalContainer().kindErasure(ctx).empty
    }

    def 'has no collect — supplying no collect is what makes the kind a presence wrapper'() {
        expect:
        new OptionalContainer().collect().empty
    }

    def 'wrapNullness is NULLABLE — ofNullable is null-safe'() {
        expect:
        new OptionalContainer().wrapNullness() == Nullability.NULLABLE
    }
}
