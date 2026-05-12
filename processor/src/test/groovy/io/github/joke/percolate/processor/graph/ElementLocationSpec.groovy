package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ElementLocationSpec extends Specification {

    def 'segment returns elem(role) with default role'() {
        expect:
        new ElementLocation().segment() == 'elem(element)'
    }

    def 'encode returns elem(role) with default role'() {
        expect:
        new ElementLocation().encode() == 'elem(element)'
    }

    def 'segment returns elem(key) for key role'() {
        expect:
        new ElementLocation('key').segment() == 'elem(key)'
    }

    def 'two ElementLocation instances with same role are equal'() {
        expect:
        new ElementLocation('element') == new ElementLocation('element')
    }

    def 'two ElementLocation instances with same role have same hashCode'() {
        expect:
        new ElementLocation('element').hashCode() == new ElementLocation('element').hashCode()
    }

    def 'two ElementLocation instances with different roles are not equal'() {
        expect:
        new ElementLocation('key') != new ElementLocation('value')
    }

    def 'no-arg constructor defaults to element role'() {
        expect:
        new ElementLocation().getRole() == 'element'
    }
}
