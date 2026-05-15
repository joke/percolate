package io.github.joke.percolate.processor.test

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TypeUniverseSpec extends Specification {

    def 'pool contains expected number of types'() {
        expect:
        TypeUniverse.pool().size() == 10
    }

    def 'types util is consistent with fields'() {
        expect:
        TypeUniverse.types().isSameType(TypeUniverse.STRING, TypeUniverse.STRING)
    }

    def 'elements util resolves String'() {
        expect:
        TypeUniverse.elements().getTypeElement('java.lang.String') != null
    }

    def 'primitive and boxed are distinct'() {
        expect:
        !TypeUniverse.types().isSameType(TypeUniverse.INT, TypeUniverse.INTEGER)
    }
}
