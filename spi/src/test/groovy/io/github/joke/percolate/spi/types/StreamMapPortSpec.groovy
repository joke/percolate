package io.github.joke.percolate.spi.types

import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.variable

/**
 * SPIKE task 1.2 — the {@code StreamMap} strategy's type decisions ported to the model: the erasure match that
 * replaces {@code Containers.isStream}, the element extraction that replaces {@code Containers.typeArgument},
 * and the functor-lift grounding ({@code match} + {@code ground}) that replaces the engine-side
 * {@code PortType} unify/ground over mirrors. Proves a container strategy is authorable against
 * {@code TypeSpace} with no {@code javax.lang.model} import.
 */
@Tag('unit')
class StreamMapPortSpec extends Specification {

    static final String STREAM = 'java.util.stream.Stream'
    static final TypeRef HUMAN = declared('com.acme.Human')
    static final TypeRef PERSON = declared('com.acme.Person')

    def space = TypeSpace.of()

    def 'a Stream demand matches on erasure and yields its element type'() {
        def target = declared(STREAM, HUMAN)

        expect:
        space.isSameType(space.erasure(target), declared(STREAM))
        (target as TypeRef.Declared).args[0] == HUMAN
    }

    def 'a non-Stream demand is rejected by the erasure match'() {
        def target = declared('java.util.List', HUMAN)

        expect:
        !space.isSameType(space.erasure(target), declared(STREAM))
    }

    def 'the lift port Stream<A> is grounded by matching a concrete Stream source'() {
        def liftPort = declared(STREAM, variable('A'))
        def bindings = space.match(liftPort, declared(STREAM, PERSON))

        expect:
        bindings.get() == [A: PERSON]
        space.ground(liftPort, bindings.get()) == declared(STREAM, PERSON)
    }

    def 'the lift port does not match a source of a different container kind'() {
        expect:
        space.match(declared(STREAM, variable('A')), declared('java.util.List', PERSON)).empty
    }

    def 'a variable bound twice must bind consistently'() {
        def pattern = declared('java.util.Map', variable('A'), variable('A'))

        expect:
        space.match(pattern, declared('java.util.Map', PERSON, PERSON)).get() == [A: PERSON]
        space.match(pattern, declared('java.util.Map', PERSON, HUMAN)).empty
    }

    def 'grounding a nested pattern rebuilds the concrete element type'() {
        def child = space.ground(variable('A'), [A: PERSON])
        def flatMapChild = space.ground(declared(STREAM, variable('A')), [A: HUMAN])

        expect:
        child == PERSON
        flatMapChild == declared(STREAM, HUMAN)
    }
}
