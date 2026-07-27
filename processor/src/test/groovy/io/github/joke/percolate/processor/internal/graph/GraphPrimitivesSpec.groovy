package io.github.joke.percolate.processor.internal.graph

import spock.lang.Specification
import spock.lang.Tag

/**
 * The small value types of the graph package: the empty-path edge cases of {@link TargetPath} / {@link AccessPath}.
 * Pure functions — no javac substrate, so no isolation.
 */
@Tag('unit')
class GraphPrimitivesSpec extends Specification {

    def 'TargetPath.of maps a null or empty segment to the empty path and a segment to a single-element path'() {
        expect:
        TargetPath.of(null).segments.empty
        TargetPath.of('').segments.empty
        TargetPath.of('name').segments == ['name']
    }

    def 'an empty TargetPath has an empty last segment; a populated one returns its tail'() {
        expect:
        new TargetPath([]).lastSegment() == ''
        TargetPath.of('name').lastSegment() == 'name'
    }

    def 'an empty AccessPath has an empty last segment; a populated one returns its tail'() {
        expect:
        new AccessPath([]).lastSegment() == ''
        AccessPath.of('street').lastSegment() == 'street'
    }
}
