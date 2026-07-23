package io.github.joke.percolate.reactor

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link FluxContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 *
 * <p>Only the individually-overridden protected members are exercised here — {@code Container.expand()} itself is
 * inherited framework orchestration defined (and already unit-tested end-to-end, over a fake {@code Container}
 * subclass) in {@code spi}'s own {@code ContainerSpec}; its bytecode lives in {@code spi}, not {@code reactor}, so
 * re-driving it here through {@code FluxContainer} would add no additional mutant-killing power over this module's
 * own classes while creating ambiguous double coverage of these same override methods that confuses pitest's
 * mutant-to-test attribution (see the {@code clean-up-test-coverage-tooling} design notes).
 */
@Tag('unit')
class FluxContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement fluxElement = Mock()
    TypeMirror fluxOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror otherType = Mock()

    def 'matches delegates the Flux-kind question to the seam'() {
        when:
        def result = new FluxContainer().matches(fluxOfString, ctx)

        then:
        1 * ctx.isType(fluxOfString, FluxContainer.FLUX) >> true
        0 * _

        expect:
        result
    }

    def 'a non-Flux target does not match'() {
        when:
        def result = new FluxContainer().matches(otherType, ctx)

        then:
        1 * ctx.isType(otherType, FluxContainer.FLUX) >> false
        0 * _

        expect:
        !result
    }

    def 'element returns the first type argument'() {
        when:
        def result = new FluxContainer().element(fluxOfString, ctx)

        then:
        1 * ctx.typeArgument(fluxOfString, 0) >> stringType
        0 * _

        expect:
        result.is(stringType)
    }

    def 'kindErasure resolves the Flux type element when present'() {
        when:
        def result = new FluxContainer().kindErasure(ctx)

        then:
        1 * ctx.typeElementNamed(FluxContainer.FLUX) >> fluxElement
        0 * _

        expect:
        result.get().is(fluxElement)
    }

    def 'kindErasure is empty when Flux is unresolved on the compile classpath'() {
        when:
        def result = new FluxContainer().kindErasure(ctx)

        then:
        1 * ctx.typeElementNamed(FluxContainer.FLUX) >> null
        0 * _

        expect:
        result.empty
    }

    def 'intermediateErasure resolves the Flux type element (the kind is its own intermediate)'() {
        when:
        def result = new FluxContainer().intermediateErasure(ctx)

        then:
        1 * ctx.typeElementNamed(FluxContainer.FLUX) >> fluxElement
        0 * _

        expect:
        result.is(fluxElement)
    }

    def 'intermediateErasure throws when reactor-core is not on the compile classpath'() {
        when:
        new FluxContainer().intermediateErasure(ctx)

        then:
        1 * ctx.typeElementNamed(FluxContainer.FLUX) >> null
        0 * _
        def error = thrown(NullPointerException)

        expect:
        error.message.contains('reactor-core')
    }

    def 'iterate is not supported — Flux is already its own intermediate'() {
        expect:
        new FluxContainer().iterate().empty
    }

    def 'collect is not supported — Flux is already its own intermediate'() {
        expect:
        new FluxContainer().collect().empty
    }

    def 'unwrap is not supported — Flux is a sequence, not a wrapper'() {
        expect:
        new FluxContainer().unwrap().empty
    }

    def 'mapPresence is not supported — Flux is a sequence, not a wrapper'() {
        expect:
        new FluxContainer().mapPresence().empty
    }

    def 'wrap lifts a scalar into a one-element Flux via Flux.just'() {
        expect:
        new FluxContainer().wrap().get().render(CodeBlock.of('$N', 'x')).toString() == 'reactor.core.publisher.Flux.just(x)'
    }
}
