package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapperScopeSpec extends Specification {

    def 'encode returns "mapper"'() {
        expect:
        MapperScope.INSTANCE.encode() == 'mapper'
    }

    def 'INSTANCE is the only instance (singleton)'() {
        expect:
        MapperScope.INSTANCE == MapperScope.INSTANCE
    }
}
