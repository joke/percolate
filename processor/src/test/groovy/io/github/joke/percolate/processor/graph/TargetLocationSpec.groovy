package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TargetLocationSpec extends Specification {

    def 'encode returns tgt[path]'() {
        given:
        def loc = new TargetLocation(TargetPath.of('lastName'))

        expect:
        loc.encode() == 'tgt[lastName]'
    }

    def 'encode for empty path returns tgt[]'() {
        given:
        def loc = new TargetLocation(TargetPath.of(''))

        expect:
        loc.encode() == 'tgt[]'
    }

    def 'two locations with same path are equal'() {
        expect:
        new TargetLocation(TargetPath.of('lastName')).equals(new TargetLocation(TargetPath.of('lastName')))
    }

    def 'two locations with different paths are not equal'() {
        expect:
        !new TargetLocation(TargetPath.of('a')).equals(new TargetLocation(TargetPath.of('b')))
    }
}
