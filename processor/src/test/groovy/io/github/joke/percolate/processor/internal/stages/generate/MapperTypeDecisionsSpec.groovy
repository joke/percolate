package io.github.joke.percolate.processor.internal.stages.generate

import spock.lang.Specification
import spock.lang.Tag

import static javax.lang.model.element.ElementKind.CLASS
import static javax.lang.model.element.ElementKind.ENUM
import static javax.lang.model.element.ElementKind.INTERFACE
import static javax.lang.model.element.Modifier.FINAL
import static javax.lang.model.element.Modifier.PUBLIC

/**
 * {@link MapperTypeDecisions} unit-tested on plain inputs — the pure assembly decisions split out of
 * {@code AssembleMapperType}'s {@code TypeName.get(mirror)} render/{@code Filer}-write leaf: the finality of a
 * generated {@code public} member and of a parameter, and whether a mapper's {@code ElementKind} means the impl
 * {@code implements} or {@code extends}. No javac substrate — booleans and the {@code ElementKind} enum only.
 */
@Tag('unit')
class MapperTypeDecisionsSpec extends Specification {

    MapperTypeDecisions decisions = new MapperTypeDecisions()

    def 'publicModifiers is PUBLIC, plus FINAL exactly when the finality switch is on'() {
        expect:
        decisions.publicModifiers(makeFinal).toList() == expected

        where:
        makeFinal | expected
        false     | [PUBLIC]
        true      | [PUBLIC, FINAL]
    }

    def 'parameterModifiers is FINAL when on, and no modifier at all when off'() {
        expect:
        decisions.parameterModifiers(makeFinal).toList() == expected

        where:
        makeFinal | expected
        false     | []
        true      | [FINAL]
    }

    def 'isInterface is true only for an INTERFACE kind'() {
        expect:
        decisions.isInterface(kind) == expected

        where:
        kind      | expected
        INTERFACE | true
        CLASS     | false
        ENUM      | false
    }
}
