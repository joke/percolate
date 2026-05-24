package io.github.joke.percolate.processor.stages.generate

import com.palantir.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class IncomingValuesImplSpec extends Specification {

    def 'byName returns the correct CodeBlock for a known slot name'() {
        given:
        final var cb1 = CodeBlock.of('firstNameValue')
        final var cb2 = CodeBlock.of('lastNameValue')
        final var incoming = new IncomingValuesImpl(
                List.of(cb1, cb2),
                Map.of('firstName', cb1, 'lastName', cb2))

        when:
        final var result = incoming.byName('firstName')

        then:
        result == cb1
    }

    def 'byGroupPosition returns the correct CodeBlock by index'() {
        given:
        final var cb1 = CodeBlock.of('first')
        final var cb2 = CodeBlock.of('second')
        final var incoming = new IncomingValuesImpl(
                List.of(cb1, cb2),
                Map.of())

        when:
        final var result0 = incoming.byGroupPosition(0)
        final var result1 = incoming.byGroupPosition(1)

        then:
        result0 == cb1
        result1 == cb2
    }

    def 'single() returns the only positional entry when list has size 1'() {
        given:
        final var cb = CodeBlock.of('onlyValue')
        final var incoming = new IncomingValuesImpl(List.of(cb), Map.of())

        when:
        final var result = incoming.single()

        then:
        result == cb
    }

    def 'single() throws when list is empty'() {
        given:
        final var incoming = new IncomingValuesImpl(List.of(), Map.of())

        when:
        incoming.single()

        then:
        thrown(IllegalStateException)
    }

    def 'single() throws when list has more than one entry'() {
        given:
        final var cb1 = CodeBlock.of('first')
        final var cb2 = CodeBlock.of('second')
        final var incoming = new IncomingValuesImpl(List.of(cb1, cb2), Map.of())

        when:
        incoming.single()

        then:
        thrown(IllegalStateException)
    }

    def 'byName throws for unknown slot name'() {
        given:
        final var incoming = new IncomingValuesImpl(
                List.of(CodeBlock.of('x')),
                Map.of())

        when:
        incoming.byName('unknown')

        then:
        thrown(IllegalStateException)
    }

    def 'of() creates an IncomingValues with a single entry'() {
        given:
        final var cb = CodeBlock.of('singleValue')

        when:
        final var incoming = IncomingValuesImpl.of(cb)

        then:
        incoming.single() == cb
    }
}
