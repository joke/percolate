package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ElementLocationSpec extends Specification {

    def 'ElementLocation segment is elem'() {
        expect:
        new ElementLocation().segment() == 'elem'
    }

    def 'ElementLocation encode is elem'() {
        expect:
        new ElementLocation().encode() == 'elem'
    }

    def 'two ElementLocation instances are equal'() {
        expect:
        new ElementLocation() == new ElementLocation()
    }

    def 'two ElementLocation instances have same hashCode'() {
        expect:
        new ElementLocation().hashCode() == new ElementLocation().hashCode()
    }
}
