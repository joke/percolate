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
        def callTarget = new MethodSig('map', [PERSON], HUMAN)
        def scopeMethod = new MethodSig('map', [declared('com.acme.Person')], declared('com.acme.Human'))

        expect:
        callTarget == scopeMethod
        callTarget.hashCode() == scopeMethod.hashCode()
    }

    def 'a signature differing in name or parameter types is distinct'() {
        def method = new MethodSig('map', [PERSON], HUMAN)

        expect:
        method != new MethodSig('convert', [PERSON], HUMAN)
        method != new MethodSig('map', [HUMAN], HUMAN)
        method != new MethodSig('map', [PERSON, PERSON], HUMAN)
    }

    def 'signatures key sets and maps directly'() {
        def parameterRoots = [new MethodSig('map', [PERSON], HUMAN)] as Set

        expect:
        new MethodSig('map', [PERSON], HUMAN) in parameterRoots
        !(new MethodSig('map', [HUMAN], HUMAN) in parameterRoots)
    }

    def 'the self-call decision is a value comparison'() {
        def scopeMethod = new MethodSig('map', [PERSON], HUMAN)
        def selfCall = new MethodSig('map', [PERSON], HUMAN)
        def delegation = new MethodSig('other', [PERSON], HUMAN)
        def voidSibling = new MethodSig('map', [PERSON], none())

        expect:
        selfCall == scopeMethod
        delegation != scopeMethod
        voidSibling != scopeMethod
    }
}
