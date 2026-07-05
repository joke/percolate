package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link ListContainer} type-detection unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}): the list-kind question is stubbed on a mocked {@code ResolveCtx} and the
 * {@link TypeMirror} is an opaque never-interrogated token. No javac. (The container's codegen output is covered
 * against a real compiler in {@link ListContainerSpec}.)
 */
@Tag('unit')
class ListContainerSeamSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror type = Mock()

    def 'matches delegates the list-kind question to the seam'() {
        ctx.isList(type) >> true

        expect:
        new ListContainer().matches(type, ctx)
    }

    def 'a non-list target does not match'() {
        ctx.isList(type) >> false

        expect:
        !new ListContainer().matches(type, ctx)
    }
}
