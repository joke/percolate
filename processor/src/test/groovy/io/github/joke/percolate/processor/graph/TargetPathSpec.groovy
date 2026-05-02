package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TargetPathSpec extends Specification {

    def 'of creates a single-segment path'() {
        expect:
        TargetPath.of('name').segments == ['name']
    }

    def 'append produces a new path leaving original unchanged'() {
        given:
        def original = TargetPath.of('name')

        when:
        def extended = original.append('first')

        then:
        extended.segments == ['name', 'first']
        original.segments == ['name']
    }

    def 'toString joins segments with dots'() {
        expect:
        TargetPath.of('name').toString() == 'name'
        TargetPath.of('name').append('first').toString() == 'name.first'
    }

    def 'two paths with same segments are equal'() {
        expect:
        TargetPath.of('name').equals(TargetPath.of('name'))
    }

    def 'two paths with different segments are not equal'() {
        expect:
        !TargetPath.of('name').equals(TargetPath.of('address'))
    }

    def 'hashCode is consistent with equals'() {
        expect:
        TargetPath.of('name').hashCode() == TargetPath.of('name').hashCode()
    }
}
