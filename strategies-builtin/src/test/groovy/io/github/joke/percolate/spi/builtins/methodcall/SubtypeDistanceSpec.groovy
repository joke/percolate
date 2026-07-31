package io.github.joke.percolate.spi.builtins.methodcall

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
        ctx.isAssignable(from, to) >> true

        expect:
        new SubtypeDistance().between(from, to, ctx) == 0
    }

    def 'pins current behaviour: a non-assignable input also returns distance 0'() {
        ctx.isSameType(from, to) >> false
        ctx.isAssignable(from, to) >> false
        // A walkable one-hop chain is in place, so only the assignability short-circuit can be what returns 0.
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> to
        ctx.isDeclared(to) >> true
        ctx.isSameType(to, to) >> true

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

    // The opening same-type answer wins outright: a walkable supertype chain that would also reach the target is
    // never entered, so an identity conversion is never charged a hop.
    def 'bfsDistance returns 0 immediately when start and target are the same type'() {
        TypeMirror mid = Mock()
        ctx.isSameType(from, to) >> true
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> mid
        ctx.isDeclared(mid) >> true
        ctx.isSameType(mid, to) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 0
    }

    def 'bfsDistance finds the target at the first hop, distance 1'() {
        ctx.isSameType(from, to) >> false
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> to
        ctx.isDeclared(to) >> true
        ctx.isSameType(to, to) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 1
    }

    def 'bfsDistance returns 0 when the start type is not declared (no supertype to walk)'() {
        ctx.isSameType(from, to) >> false
        ctx.isDeclared(from) >> false
        // A supertype the walk would otherwise land on immediately, so only the declared-check can stop it.
        ctx.superclassOf(from) >> to
        ctx.isDeclared(to) >> true
        ctx.isSameType(to, to) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 0
    }

    def 'bfsDistance returns 0 when the direct supertype is not declared (dead end)'() {
        TypeMirror mid = Mock()
        ctx.isSameType(from, to) >> false
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> mid
        ctx.isDeclared(mid) >> false
        // Same-type with the target, so an undeclared hop that were walked anyway would report distance 1.
        ctx.isSameType(mid, to) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 0
    }

    def 'bfsDistance returns 0 when the supertype chain cycles back to an already-visited type'() {
        ctx.isSameType(from, to) >> false
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> from
        ctx.isDeclared(from) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 0
    }

    // Visited-ness is keyed by the type's text, not by mirror identity: a second mirror for the same type closes the
    // cycle just as the first would, and the start type is seeded as visited before the walk begins.
    def 'bfsDistance treats a distinct mirror naming a visited type as already visited'() {
        TypeMirror mid = Mock()
        TypeMirror startAgain = Mock()
        from.toString() >> 'com.example.A'
        startAgain.toString() >> 'com.example.A'
        mid.toString() >> 'com.example.B'
        ctx.isSameType(from, to) >> false
        ctx.isDeclared(from) >> true
        ctx.superclassOf(from) >> mid
        ctx.isDeclared(mid) >> true
        ctx.isSameType(mid, to) >> false
        ctx.superclassOf(mid) >> startAgain
        ctx.isDeclared(startAgain) >> true
        // Would report distance 2 if the walk were allowed back onto the start type.
        ctx.isSameType(startAgain, to) >> true

        expect:
        new SubtypeDistance().bfsDistance(from, to, ctx) == 0
    }
}
