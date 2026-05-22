package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ScopeTransitionSpec extends Specification {

    def 'enum declares exactly three constants in documented order'() {
        expect:
        ScopeTransition.values() == [
                ScopeTransition.PRESERVING,
                ScopeTransition.ENTERING,
                ScopeTransition.EXITING,
        ] as ScopeTransition[]
    }

    def 'PRESERVING is the natural default for same-scope bridges'() {
        expect:
        ScopeTransition.PRESERVING.name() == 'PRESERVING'
    }

    def 'ENTERING marks a scope-enter bridge'() {
        expect:
        ScopeTransition.ENTERING.name() == 'ENTERING'
    }

    def 'EXITING marks a scope-exit bridge'() {
        expect:
        ScopeTransition.EXITING.name() == 'EXITING'
    }
}
