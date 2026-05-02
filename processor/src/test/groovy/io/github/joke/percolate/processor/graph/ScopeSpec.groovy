package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ScopeSpec extends Specification {

    def 'MapperScope encode returns "mapper"'() {
        expect:
        MapperScope.INSTANCE.encode() == 'mapper'
    }

    def 'MapperScope is a singleton'() {
        expect:
        MapperScope.INSTANCE == MapperScope.INSTANCE
    }
}
