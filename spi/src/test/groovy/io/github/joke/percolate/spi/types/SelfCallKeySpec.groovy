package io.github.joke.percolate.spi.types

import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.none

/**
 * SPIKE task 1.3 — the engine's string-keyed signature comparisons ported to the model: {@code SelfCallGuard}
 * compares {@code name(paramType.toString(),…)} strings and {@code MethodScope}/{@code Value.id()} encode
 * {@code TypeMirror::toString} keys because mirrors have no value equality. A {@code MethodSig} of
 * {@code TypeRef}s is a plain value, so the same decisions are direct {@code equals} — no string keys.
 */
@Tag('unit')
class SelfCallKeySpec extends Specification {

    static final TypeRef PERSON = declared('com.acme.Person')
    static final TypeRef HUMAN = declared('com.acme.Human')

    def 'independently built signatures of the same method are equal'() {
        def callTarget = MethodSig.of('map', HUMAN, PERSON)
        def scopeMethod = MethodSig.of('map', declared('com.acme.Human'), declared('com.acme.Person'))

        expect:
        callTarget == scopeMethod
        callTarget.hashCode() == scopeMethod.hashCode()
    }

    def 'a signature differing in name or parameter types is distinct'() {
        def method = MethodSig.of('map', HUMAN, PERSON)

        expect:
        method != MethodSig.of('convert', HUMAN, PERSON)
        method != MethodSig.of('map', HUMAN, HUMAN)
        method != MethodSig.of('map', HUMAN, PERSON, PERSON)
    }

    def 'signatures key sets and maps directly'() {
        def parameterRoots = [MethodSig.of('map', HUMAN, PERSON)] as Set

        expect:
        MethodSig.of('map', HUMAN, PERSON) in parameterRoots
        !(MethodSig.of('map', HUMAN, HUMAN) in parameterRoots)
    }

    def 'the self-call decision is a value comparison'() {
        def scopeMethod = MethodSig.of('map', HUMAN, PERSON)
        def selfCall = MethodSig.of('map', HUMAN, PERSON)
        def delegation = MethodSig.of('other', HUMAN, PERSON)
        def voidSibling = MethodSig.of('map', none(), PERSON)

        expect:
        selfCall == scopeMethod
        delegation != scopeMethod
        voidSibling != scopeMethod
    }
}
