package io.github.joke.percolate.spi.test

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TypeUniverseSpec extends Specification {

    def 'of(Class) resolves the same element as element(String)'() {
        expect:
        TypeUniverse.of(String) == TypeUniverse.element('java.lang.String')
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
