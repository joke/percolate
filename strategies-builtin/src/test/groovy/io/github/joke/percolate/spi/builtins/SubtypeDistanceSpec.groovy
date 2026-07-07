package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link SubtypeDistance} unit-tested mock-only over the {@link ResolveCtx} type-query seam: the BFS walk it performs
 * is driven entirely by stubbed seam questions ({@code isSameType}/{@code isAssignable}/{@code superclassOf}/
 * {@code isDeclared}), never a real type hierarchy. {@link TypeMirror} tokens are opaque and never stubbed themselves.
 */
@Tag('unit')
class SubtypeDistanceSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeMirror from = Mock()
    TypeMirror to = Mock()

    def 'same-type return distance is 0'() {
        ctx.isSameType(from, to) >> true

        expect:
        new SubtypeDistance().between(from, to, ctx) == 0
    }

    def 'pins current behaviour: a non-assignable input also returns distance 0'() {
        ctx.isSameType(from, to) >> false
        ctx.isAssignable(from, to) >> false

        expect:
        // FOLLOW-UP: same-type and non-assignable both collapse to distance 0 — a distance and a "no path" outcome
        // are conflated in the current walk; carried forward unchanged from the pre-extraction behaviour.
        new SubtypeDistance().between(from, to, ctx) == 0
    }

    def 'walks a multi-hop supertype chain to find the target'() {
        TypeMirror mid = Mock()

        ctx.isSameType(from, to) >> false
        ctx.isAssignable(from, to) >> true
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> mid
        ctx.isDeclared(mid) >> true
        ctx.isSameType(mid, to) >> false
        ctx.superclassOf(mid) >> to
        ctx.isDeclared(to) >> true
        ctx.isSameType(to, to) >> true

        expect:
        new SubtypeDistance().between(from, to, ctx) == 2
    }
}
