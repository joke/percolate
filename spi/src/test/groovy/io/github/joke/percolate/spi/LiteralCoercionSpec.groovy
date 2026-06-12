package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeKind

@Tag('unit')
class LiteralCoercionSpec extends Specification {

    def types = TypeUniverse.types()

    def 'String target coerces to the raw value verbatim'() {
        expect:
        render(TypeUniverse.STRING, 'hello') == '"hello"'
    }

    def 'int coerces to a bare literal'() {
        expect:
        render(TypeUniverse.INT, '7') == '7'
    }

    def 'long renders with the L suffix'() {
        expect:
        render(TypeUniverse.LONG, '42') == '42L'
    }

    def 'a wrapper target coerces like its primitive, as a wrapper-typed expression'() {
        expect:
        render(TypeUniverse.INTEGER, '7') == 'java.lang.Integer.valueOf(7)'
    }

    def 'an out-of-scope (enum) target fails coercion'() {
        expect:
        LiteralCoercion.coerce('MONDAY', TypeUniverse.DAY_OF_WEEK).empty
    }

    def 'char accepts exactly one character'() {
        expect:
        render(charType(), 'a') == "'a'"
    }

    def 'char rejects multi-character strings'() {
        expect:
        LiteralCoercion.coerce('AB', charType()).empty
    }

    def 'boolean accepts only canonical true/false'() {
        expect:
        render(booleanType(), 'true') == 'true'
    }

    def 'boolean rejects non-canonical text'() {
        expect:
        LiteralCoercion.coerce('yes', booleanType()).empty
    }

    def 'numeric overflow fails rather than truncating'() {
        expect:
        LiteralCoercion.coerce('999', byteType()).empty
    }

    def 'the raw string is not whitespace-trimmed'() {
        expect:
        LiteralCoercion.coerce(' 7', TypeUniverse.INT).empty
    }

    private String render(final type, final String raw) {
        LiteralCoercion.coerce(raw, type).orElseThrow().toString()
    }

    private byteType() { types.getPrimitiveType(TypeKind.BYTE) }

    private charType() { types.getPrimitiveType(TypeKind.CHAR) }

    private booleanType() { types.getPrimitiveType(TypeKind.BOOLEAN) }
}
