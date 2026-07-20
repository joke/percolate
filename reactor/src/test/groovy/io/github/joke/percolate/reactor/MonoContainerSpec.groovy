package io.github.joke.percolate.reactor

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link MonoContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 *
 * <p>Only the individually-overridden protected members are exercised here — {@code Container.expand()} itself is
 * inherited framework orchestration defined (and already unit-tested end-to-end, over a fake {@code Container}
 * subclass) in {@code spi}'s own {@code ContainerSpec}; its bytecode lives in {@code spi}, not {@code reactor}, so
 * re-driving it here through {@code MonoContainer} would add no additional mutant-killing power over this module's
 * own classes while creating ambiguous double coverage of these same override methods that confuses pitest's
 * mutant-to-test attribution (see the {@code clean-up-test-coverage-tooling} design notes). {@code unwrap} is
 * pinned as unsupported: collapsing a {@code Mono} to a scalar is {@code block()}, which lives only in
 * {@code reactor-blocking}.
 */
@Tag('unit')
class MonoContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement monoElement = Mock()
    TypeElement fluxElement = Mock()
    TypeMirror monoOfString = Mock()
    TypeMirror otherType = Mock()

    def 'matches delegates the Mono-kind question to the seam'() {
        when:
        def result = new MonoContainer().matches(monoOfString, ctx)

        then:
        1 * ctx.isType(monoOfString, MonoContainer.MONO) >> true
        0 * _

        expect:
        result
    }

    def 'a non-Mono target does not match'() {
        when:
        def result = new MonoContainer().matches(otherType, ctx)

        then:
        1 * ctx.isType(otherType, MonoContainer.MONO) >> false
        0 * _

        expect:
        !result
    }

    def 'element returns the first type argument'() {
        TypeMirror stringType = Mock()

        when:
        def result = new MonoContainer().element(monoOfString, ctx)

        then:
        1 * ctx.typeArgument(monoOfString, 0) >> stringType
        0 * _

        expect:
        result.is(stringType)
    }

    def 'kindErasure resolves the Mono type element when present'() {
        when:
        def result = new MonoContainer().kindErasure(ctx)

        then:
        1 * ctx.typeElementNamed(MonoContainer.MONO) >> monoElement
        0 * _

        expect:
        result.get().is(monoElement)
    }

    def 'kindErasure is empty when Mono is unresolved on the compile classpath'() {
        when:
        def result = new MonoContainer().kindErasure(ctx)

        then:
        1 * ctx.typeElementNamed(MonoContainer.MONO) >> null
        0 * _

        expect:
        result.empty
    }

    def 'intermediateErasure resolves the shared Flux type element'() {
        when:
        def result = new MonoContainer().intermediateErasure(ctx)

        then:
        1 * ctx.typeElementNamed(MonoContainer.FLUX) >> fluxElement
        0 * _

        expect:
        result.is(fluxElement)
    }

    def 'intermediateErasure throws when reactor-core is not on the compile classpath'() {
        when:
        new MonoContainer().intermediateErasure(ctx)

        then:
        1 * ctx.typeElementNamed(MonoContainer.FLUX) >> null
        0 * _
        def error = thrown(NullPointerException)

        expect:
        error.message.contains('reactor-core')
    }

    def 'collect is not supported — Mono is a presence wrapper, not a sequence'() {
        expect:
        new MonoContainer().collect().empty
    }

    def 'unwrap is not supported — collapsing to a scalar is block(), reserved for reactor-blocking'() {
        expect:
        new MonoContainer().unwrap().empty
    }

    def 'iterate opens a Mono into its Flux intermediate via mono.flux()'() {
        expect:
        CodeBlock.of('$L\n', new MonoContainer().iterate().get().render(CodeBlock.of('$N', 'm'))).toString() == 'm.flux()\n'
    }

    def 'wrap lifts a non-null scalar into a Mono via Mono.just'() {
        expect:
        new MonoContainer().wrap().get().render(CodeBlock.of('$N', 'x')).toString() == 'reactor.core.publisher.Mono.just(x)'
    }

    def 'mapPresence maps the wrapped value as a same-kind functor lift via mono.map'() {
        expect:
        CodeBlock.of('$L\n', new MonoContainer().mapPresence().get().weave(CodeBlock.of('$N', 'm'), 'v', CodeBlock.of('$N', 'b')))
                .toString() == 'm.map(v -> b)\n'
    }
}
