package io.github.joke.percolate.spi.builtins

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link Labels} unit-tested over raw {@code javax.lang.model} tokens (never a {@link io.github.joke.percolate.spi.ResolveCtx}
 * question — {@code simple}/{@code conversion} read only the type's own {@code getKind}/{@code asElement}/
 * {@code getTypeArguments} structure). No javac.
 */
@Tag('unit')
class LabelsSpec extends Specification {

    def 'simple falls back to toString for a non-declared type (e.g. a primitive)'() {
        TypeMirror intType = Mock()
        intType.kind >> TypeKind.INT
        intType.toString() >> 'int'

        expect:
        Labels.simple(intType) == 'int'
    }

    def 'simple is the bare element name for a declared type with no type arguments'() {
        DeclaredType stringType = Mock()
        TypeElement stringElement = Mock()
        stringType.kind >> TypeKind.DECLARED
        stringType.asElement() >> stringElement
        stringElement.simpleName >> nameOf('String')
        stringType.typeArguments >> []

        expect:
        Labels.simple(stringType) == 'String'
    }

    def 'simple recurses into a single type argument'() {
        DeclaredType optionalOfString = Mock()
        TypeElement optionalElement = Mock()
        DeclaredType stringType = Mock()
        TypeElement stringElement = Mock()
        optionalOfString.kind >> TypeKind.DECLARED
        optionalOfString.asElement() >> optionalElement
        optionalElement.simpleName >> nameOf('Optional')
        optionalOfString.typeArguments >> [stringType]
        stringType.kind >> TypeKind.DECLARED
        stringType.asElement() >> stringElement
        stringElement.simpleName >> nameOf('String')
        stringType.typeArguments >> []

        expect:
        Labels.simple(optionalOfString) == 'Optional<String>'
    }

    def 'simple recurses into and joins multiple type arguments with a comma-space'() {
        DeclaredType mapOfStringInteger = Mock()
        TypeElement mapElement = Mock()
        DeclaredType stringType = Mock()
        TypeElement stringElement = Mock()
        DeclaredType integerType = Mock()
        TypeElement integerElement = Mock()
        mapOfStringInteger.kind >> TypeKind.DECLARED
        mapOfStringInteger.asElement() >> mapElement
        mapElement.simpleName >> nameOf('Map')
        mapOfStringInteger.typeArguments >> [stringType, integerType]
        stringType.kind >> TypeKind.DECLARED
        stringType.asElement() >> stringElement
        stringElement.simpleName >> nameOf('String')
        stringType.typeArguments >> []
        integerType.kind >> TypeKind.DECLARED
        integerType.asElement() >> integerElement
        integerElement.simpleName >> nameOf('Integer')
        integerType.typeArguments >> []

        expect:
        Labels.simple(mapOfStringInteger) == 'Map<String, Integer>'
    }

    def 'simple recurses through nested generic arguments (e.g. Optional<Set<Address>>)'() {
        DeclaredType optionalOfSet = Mock()
        TypeElement optionalElement = Mock()
        DeclaredType setOfAddress = Mock()
        TypeElement setElement = Mock()
        DeclaredType addressType = Mock()
        TypeElement addressElement = Mock()
        optionalOfSet.kind >> TypeKind.DECLARED
        optionalOfSet.asElement() >> optionalElement
        optionalElement.simpleName >> nameOf('Optional')
        optionalOfSet.typeArguments >> [setOfAddress]
        setOfAddress.kind >> TypeKind.DECLARED
        setOfAddress.asElement() >> setElement
        setElement.simpleName >> nameOf('Set')
        setOfAddress.typeArguments >> [addressType]
        addressType.kind >> TypeKind.DECLARED
        addressType.asElement() >> addressElement
        addressElement.simpleName >> nameOf('Address')
        addressType.typeArguments >> []

        expect:
        Labels.simple(optionalOfSet) == 'Optional<Set<Address>>'
    }

    def 'conversion joins the from/to simple names with the glyph arrow'() {
        TypeMirror intType = Mock()
        DeclaredType longWrapperType = Mock()
        TypeElement longElement = Mock()
        intType.kind >> TypeKind.INT
        intType.toString() >> 'int'
        longWrapperType.kind >> TypeKind.DECLARED
        longWrapperType.asElement() >> longElement
        longElement.simpleName >> nameOf('Long')
        longWrapperType.typeArguments >> []

        expect:
        Labels.conversion(intType, longWrapperType) == 'int' + Labels.ARROW + 'Long'
        Labels.conversion(intType, longWrapperType) == 'int→Long'
    }

    private static Name nameOf(final String value) {
        [contentEquals: { CharSequence cs -> cs.toString() == value }, toString: { value }] as Name
    }
}
