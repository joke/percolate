package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

@Tag('unit')
class FixtureTypeSmokeSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def 'resolves PersonRecord fixture'() {
        expect:
        def element = javac.of(io.github.joke.percolate.spi.builtins.fixtures.PersonRecord)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'resolves PersonBean fixture'() {
        expect:
        def element = javac.of(io.github.joke.percolate.spi.builtins.fixtures.PersonBean)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'resolves BooleanBean fixture'() {
        expect:
        def element = javac.of(io.github.joke.percolate.spi.builtins.fixtures.BooleanBean)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }

    def 'resolves PersonByFieldOrder fixture'() {
        expect:
        def element = javac.of(io.github.joke.percolate.spi.builtins.fixtures.PersonByFieldOrder)
        element != null
        element instanceof TypeElement
        element.asType() instanceof DeclaredType
    }
}
