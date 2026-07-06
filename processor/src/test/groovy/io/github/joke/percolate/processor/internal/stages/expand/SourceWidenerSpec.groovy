package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.SourceProjection
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link SourceWidener} unit-tested mock-only: the {@link ResolveCtx} is never queried directly by this collaborator
 * (it is only threaded through to each {@link SourceProjection}), so the spec mocks the projection instead.
 */
@Tag('unit')
class SourceWidenerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror sourceA = Mock()
    TypeMirror sourceB = Mock()
    TypeMirror derived = Mock()

    def 'with no projections registered, widen returns the very same sources list, uncopied'() {
        SourceWidener widener = new SourceWidener(ctx, [])
        def sources = [sourceA, sourceB]

        expect:
        widener.widen(sources).is(sources)
    }

    def 'widen appends each projection\'s one-step view of every source'() {
        SourceProjection projection = Mock()
        SourceWidener widener = new SourceWidener(ctx, [projection])

        when:
        def widened = widener.widen([sourceA, sourceB])

        then:
        1 * projection.project(sourceA, ctx) >> Stream.of(derived)
        1 * projection.project(sourceB, ctx) >> Stream.empty()
        0 * _

        expect:
        widened == [sourceA, sourceB, derived]
    }

    def 'widen accumulates the views of every registered projection'() {
        SourceProjection first = Mock()
        SourceProjection second = Mock()
        SourceWidener widener = new SourceWidener(ctx, [first, second])

        when:
        def widened = widener.widen([sourceA])

        then:
        1 * first.project(sourceA, ctx) >> Stream.empty()
        1 * second.project(sourceA, ctx) >> Stream.of(derived)
        0 * _

        expect:
        widened == [sourceA, derived]
    }
}
