package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

@Tag('unit')
class FixtureTypeSmokeSpec extends Specification {

    def 'TypeUniverse resolves PersonRecord fixture'() {
        expect:
        def element = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonRecord)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'TypeUniverse resolves PersonBean fixture'() {
        expect:
        def element = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonBean)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'TypeUniverse resolves BooleanBean fixture'() {
        expect:
        def element = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.BooleanBean)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'TypeUniverse resolves PersonByFieldOrder fixture'() {
        expect:
        def element = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonByFieldOrder)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }
}
