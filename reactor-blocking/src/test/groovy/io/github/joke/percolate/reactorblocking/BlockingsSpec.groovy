package io.github.joke.percolate.reactorblocking

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link Blockings} unit-tested mock-only over the {@link ResolveCtx} type-query seam, mirroring the
 * {@code cutover-strategies-to-mock-seam} discipline used throughout {@code strategies-builtin}: every seam question
 * is a validated, strictly-verified interaction on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class BlockingsSpec extends Specification {

    def 'declared() resolves to a concrete Optional when the fqn is on the compile classpath'() {
        ResolveCtx ctx = Mock()
        TypeElement fluxElement = Mock()
        TypeMirror stringType = Mock()
        TypeMirror fluxOfString = Mock()

        when:
        def result = Blockings.declared(ctx, Blockings.FLUX, stringType)

        then:
        1 * ctx.typeElementNamed(Blockings.FLUX) >> fluxElement
        1 * ctx.declaredType(fluxElement, stringType) >> fluxOfString
        0 * _

        expect:
        result == Optional.of(fluxOfString)
    }

    def 'declared() is empty when the fqn does not resolve on the compile classpath'() {
        ResolveCtx ctx = Mock()
        TypeMirror stringType = Mock()

        when:
        def result = Blockings.declared(ctx, Blockings.FLUX, stringType)

        then:
        1 * ctx.typeElementNamed(Blockings.FLUX) >> null
        0 * _

        expect:
        result == Optional.empty()
    }

    def 'view() is empty when source is not a declared type'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> false
        0 * _

        expect:
        result.toList().empty
    }

    def 'view() is empty when source does not match the requested kind fqn'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.isType(source, Blockings.FLUX) >> false
        0 * _

        expect:
        result.toList().empty
    }

    def 'view() is empty when source has other than exactly one type argument'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.isType(source, Blockings.FLUX) >> true
        1 * ctx.typeArgumentCount(source) >> 2
        0 * _

        expect:
        result.toList().empty
    }

    def 'view() is empty when the sole type argument is not a reference type'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()
        TypeMirror arg = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.isType(source, Blockings.FLUX) >> true
        1 * ctx.typeArgumentCount(source) >> 1
        1 * ctx.typeArgument(source, 0) >> arg
        1 * ctx.isReferenceType(arg) >> false
        0 * _

        expect:
        result.toList().empty
    }

    def 'view() is empty when the target fqn does not resolve on the compile classpath'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()
        TypeMirror arg = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.isType(source, Blockings.FLUX) >> true
        1 * ctx.typeArgumentCount(source) >> 1
        1 * ctx.typeArgument(source, 0) >> arg
        1 * ctx.isReferenceType(arg) >> true
        1 * ctx.typeElementNamed(Blockings.STREAM) >> null
        0 * _

        expect:
        result.toList().empty
    }

    def 'view() projects source onto targetFqn<X> when source is exactly kindFqn<X> with a reference X'() {
        ResolveCtx ctx = Mock()
        TypeMirror source = Mock()
        TypeMirror arg = Mock()
        TypeElement streamElement = Mock()
        TypeMirror streamOfArg = Mock()

        when:
        def result = Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx)

        then:
        1 * ctx.isDeclared(source) >> true
        1 * ctx.isType(source, Blockings.FLUX) >> true
        1 * ctx.typeArgumentCount(source) >> 1
        2 * ctx.typeArgument(source, 0) >> arg
        1 * ctx.isReferenceType(arg) >> true
        1 * ctx.typeElementNamed(Blockings.STREAM) >> streamElement
        1 * ctx.declaredType(streamElement, arg) >> streamOfArg
        0 * _

        expect:
        result.toList() == [streamOfArg]
    }

    def 'isBlockableScalar() is false for a non-declared type'() {
        ResolveCtx ctx = Mock()
        TypeMirror type = Mock()

        when:
        def result = Blockings.isBlockableScalar(type, ctx)

        then:
        1 * ctx.isDeclared(type) >> false
        0 * _

        expect:
        !result
    }

    def 'isBlockableScalar() is false for a Mono, which is itself reactive'() {
        ResolveCtx ctx = Mock()
        TypeMirror type = Mock()

        when:
        def result = Blockings.isBlockableScalar(type, ctx)

        then:
        1 * ctx.isDeclared(type) >> true
        1 * ctx.isType(type, Blockings.MONO) >> true
        0 * _

        expect:
        !result
    }

    def 'isBlockableScalar() is false for a Flux, which is itself reactive'() {
        ResolveCtx ctx = Mock()
        TypeMirror type = Mock()

        when:
        def result = Blockings.isBlockableScalar(type, ctx)

        then:
        1 * ctx.isDeclared(type) >> true
        1 * ctx.isType(type, Blockings.MONO) >> false
        1 * ctx.isType(type, Blockings.FLUX) >> true
        0 * _

        expect:
        !result
    }

    def 'isBlockableScalar() is true for a plain declared reference type'() {
        ResolveCtx ctx = Mock()
        TypeMirror type = Mock()

        when:
        def result = Blockings.isBlockableScalar(type, ctx)

        then:
        1 * ctx.isDeclared(type) >> true
        1 * ctx.isType(type, Blockings.MONO) >> false
        1 * ctx.isType(type, Blockings.FLUX) >> false
        0 * _

        expect:
        result
    }
}
