package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class AccessPathSpec extends Specification {

    def 'of creates a single-segment path'() {
        expect:
        AccessPath.of('person').segments == ['person']
    }

    def 'append produces a new path leaving original unchanged'() {
        given:
        def original = AccessPath.of('person')

        when:
        def extended = original.append('address')

        then:
        extended.segments == ['person', 'address']
        original.segments == ['person']
    }

    def 'toString joins segments with dots'() {
        expect:
        AccessPath.of('person').toString() == 'person'
        AccessPath.of('person').append('address').toString() == 'person.address'
        AccessPath.of('a').append('b').append('c').toString() == 'a.b.c'
    }

    def 'two paths with same segments are equal'() {
        expect:
        AccessPath.of('person').equals(AccessPath.of('person'))
        AccessPath.of('a').append('b').equals(AccessPath.of('a').append('b'))
    }

    def 'two paths with different segments are not equal'() {
        expect:
        !AccessPath.of('person').equals(AccessPath.of('address'))
    }

    def 'hashCode is consistent with equals'() {
        expect:
        AccessPath.of('person').hashCode() == AccessPath.of('person').hashCode()
    }
}
