package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class SourceLocationSpec extends Specification {

    def 'encode returns src[path]'() {
        given:
        def loc = new SourceLocation(AccessPath.of('person'))

        expect:
        loc.encode() == 'src[person]'
    }

    def 'encode preserves multi-segment path'() {
        given:
        def loc = new SourceLocation(AccessPath.of('person').append('address'))

        expect:
        loc.encode() == 'src[person.address]'
    }

    def 'two locations with same path are equal'() {
        expect:
        new SourceLocation(AccessPath.of('person')).equals(new SourceLocation(AccessPath.of('person')))
    }

    def 'two locations with different paths are not equal'() {
        expect:
        !new SourceLocation(AccessPath.of('person')).equals(new SourceLocation(AccessPath.of('address')))
    }
}
