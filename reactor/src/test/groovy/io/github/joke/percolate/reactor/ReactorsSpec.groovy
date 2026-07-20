package io.github.joke.percolate.reactor

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link Reactors} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is
 * verified with its exact argument and stubbed on a mocked {@code ResolveCtx}, and every {@link TypeMirror}/
 * {@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class ReactorsSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement fluxElement = Mock()
    TypeMirror argType = Mock()
    TypeMirror fluxOfArg = Mock()

    def 'declared builds the concrete parameterised type when the fqn resolves on the compile classpath'() {
        when:
        def result = Reactors.declared(ctx, Reactors.FLUX, argType)

        then:
        1 * ctx.typeElementNamed(Reactors.FLUX) >> fluxElement
        1 * ctx.declaredType(fluxElement, argType) >> fluxOfArg
        0 * _

        expect:
        result.get().is(fluxOfArg)
    }

    def 'declared is empty when the fqn is not on the compile classpath'() {
        when:
        def result = Reactors.declared(ctx, Reactors.FLUX, argType)

        then:
        1 * ctx.typeElementNamed(Reactors.FLUX) >> null
        0 * _

        expect:
        result.empty
    }

    def 'FLUX and MONO name the reactor-core publisher types'() {
        expect:
        Reactors.FLUX == 'reactor.core.publisher.Flux'
        Reactors.MONO == 'reactor.core.publisher.Mono'
    }
}
