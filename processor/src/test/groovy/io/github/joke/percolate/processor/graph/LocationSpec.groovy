package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class LocationSpec extends Specification {

    def 'SourceLocation encode includes path'() {
        given:
        def path = AccessPath.of('person')
        def loc = new SourceLocation(path)

        expect:
        loc.encode() == 'src[person]'
    }

    def 'SourceLocation encode preserves multi-segment paths'() {
        given:
        def path = AccessPath.of('person').append('address').append('street')
        def loc = new SourceLocation(path)

        expect:
        loc.encode() == 'src[person.address.street]'
    }

    def 'TargetLocation encode includes path'() {
        given:
        def path = TargetPath.of('lastName')
        def loc = new TargetLocation(path)

        expect:
        loc.encode() == 'tgt[lastName]'
    }

    def 'TargetLocation encode for empty path'() {
        given:
        def path = TargetPath.of('')
        def loc = new TargetLocation(path)

        expect:
        loc.encode() == 'tgt[]'
    }
}
