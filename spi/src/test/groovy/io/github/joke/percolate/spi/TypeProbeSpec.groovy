package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link TypeProbe} is a thin, source-compatible forwarder onto the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}): every method delegates straight to {@code ctx}, unit-tested here against a mocked
 * {@link ResolveCtx} with opaque {@link TypeMirror} tokens. No javac.
 */
@Tag('unit')
class TypeProbeSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror type = Mock()

    def 'asTypeElement delegates to the seam, present case'() {
        TypeElement element = Mock()
        ctx.asTypeElement(type) >> Optional.of(element)

        expect:
        TypeProbe.asTypeElement(type, ctx).get().is(element)
    }

    def 'asTypeElement delegates to the seam, empty case'() {
        ctx.asTypeElement(type) >> Optional.empty()

        expect:
        !TypeProbe.asTypeElement(type, ctx).present
    }

    def 'isType delegates to the seam, true case'() {
        ctx.isType(type, 'java.util.List') >> true

        expect:
        TypeProbe.isType(type, 'java.util.List', ctx)
    }

    def 'isType delegates to the seam, false case'() {
        ctx.isType(type, 'java.util.List') >> false

        expect:
        !TypeProbe.isType(type, 'java.util.List', ctx)
    }

    def 'isEnum delegates to the seam, true case'() {
        ctx.isEnum(type) >> true

        expect:
        TypeProbe.isEnum(type, ctx)
    }

    def 'isEnum delegates to the seam, false case'() {
        ctx.isEnum(type) >> false

        expect:
        !TypeProbe.isEnum(type, ctx)
    }

    def 'simpleName delegates to the seam'() {
        ctx.simpleName(type) >> 'String'

        expect:
        TypeProbe.simpleName(type, ctx) == 'String'
    }
}
