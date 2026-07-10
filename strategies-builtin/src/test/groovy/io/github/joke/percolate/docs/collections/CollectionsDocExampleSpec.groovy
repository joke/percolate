package io.github.joke.percolate.docs.collections

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's collections page. {@code TeamMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Each method witnesses one
 * container mechanism: same-kind element conversion, cross-kind, a Stream source, and presence composed
 * inside a container.
 */
@Tag('integration')
class CollectionsDocExampleSpec extends Specification {

    TeamMapper mapper = new TeamMapperImpl()

    def 'map converts each element of the List, delegating to the element method'() {
        def team = new Team([new Member('Ada'), new Member('Grace')])

        expect:
        mapper.map(team).members*.name == ['Ada', 'Grace']
    }

    def 'toSortedTags converts a Set source into a List target'() {
        expect:
        mapper.toSortedTags(['b', 'a'] as Set).toSorted() == ['a', 'b']
    }

    def 'toUniqueTags collects a Stream source into a Set target'() {
        expect:
        mapper.toUniqueTags(['x', 'x', 'y'].stream()) == ['x', 'y'] as Set
    }

    def 'toPresentTags drops absent Optional elements while collecting'() {
        expect:
        mapper.toPresentTags([Optional.of('a'), Optional.empty(), Optional.of('b')]) == ['a', 'b'] as Set
    }

    def 'toRoster composes a List of Optional elements into an Optional Set, converting and dropping absent elements'() {
        def maybeMembers = [Optional.of(new Member('Ada')), Optional.empty(), Optional.of(new Member('Grace'))]

        expect:
        mapper.toRoster(maybeMembers).get()*.name.toSorted() == ['Ada', 'Grace']
    }
}
