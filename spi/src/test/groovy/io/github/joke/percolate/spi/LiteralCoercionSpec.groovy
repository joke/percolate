package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link LiteralCoercion} is one of the few call sites deliberately left outside the {@code ResolveCtx} type-query
 * seam (design {@code type-query-seam} Phase 3 addendum): a static utility with no {@code ResolveCtx} in reach, it
 * reads a raw {@link TypeMirror}'s kind and, for a declared target, its element's qualified name — a single-hop
 * token read, the same shape {@code ValidateNoDuplicateTargetsStageSpec} already mocks without javac. Unit-tested
 * here over mocked {@link TypeMirror}/{@link DeclaredType}/{@link TypeElement} tokens; no javac.
 */
@Tag('unit')
class LiteralCoercionSpec extends Specification {

    def 'String target coerces to the raw value verbatim'() {
        expect:
        render(declared('java.lang.String'), 'hello') == '"hello"'
    }

    def 'int coerces to a bare literal'() {
        expect:
        render(primitive(TypeKind.INT), '7') == '7'
    }

    def 'int accepts the minimum and maximum boundary values'() {
        expect:
        render(primitive(TypeKind.INT), '-2147483648') == '-2147483648'
        render(primitive(TypeKind.INT), '2147483647') == '2147483647'
    }

    def 'int rejects values one past either boundary'() {
        expect:
        LiteralCoercion.coerce('-2147483649', primitive(TypeKind.INT)).empty
        LiteralCoercion.coerce('2147483648', primitive(TypeKind.INT)).empty
    }

    def 'byte accepts the minimum and maximum boundary values'() {
        expect:
        render(primitive(TypeKind.BYTE), '-128') == '(byte) -128'
        render(primitive(TypeKind.BYTE), '127') == '(byte) 127'
    }

    def 'byte rejects values one past either boundary'() {
        expect:
        LiteralCoercion.coerce('-129', primitive(TypeKind.BYTE)).empty
        LiteralCoercion.coerce('128', primitive(TypeKind.BYTE)).empty
    }

    def 'short accepts the minimum and maximum boundary values'() {
        expect:
        render(primitive(TypeKind.SHORT), '-32768') == '(short) -32768'
        render(primitive(TypeKind.SHORT), '32767') == '(short) 32767'
    }

    def 'short rejects values one past either boundary'() {
        expect:
        LiteralCoercion.coerce('-32769', primitive(TypeKind.SHORT)).empty
        LiteralCoercion.coerce('32768', primitive(TypeKind.SHORT)).empty
    }

    def 'long renders with the L suffix and accepts its boundary values'() {
        expect:
        render(primitive(TypeKind.LONG), '42') == '42L'
        render(primitive(TypeKind.LONG), '-9223372036854775808') == '-9223372036854775808L'
        render(primitive(TypeKind.LONG), '9223372036854775807') == '9223372036854775807L'
    }

    def 'long rejects a value that does not parse as a long at all'() {
        expect:
        LiteralCoercion.coerce('99999999999999999999', primitive(TypeKind.LONG)).empty
    }

    def 'a non-numeric string fails integral coercion'() {
        expect:
        LiteralCoercion.coerce('not-a-number', primitive(TypeKind.INT)).empty
    }

    def 'float coerces to a bare literal with the f suffix'() {
        expect:
        render(primitive(TypeKind.FLOAT), '1.5') == '1.5f'
    }

    def 'float rejects non-finite input'() {
        expect:
        LiteralCoercion.coerce('NaN', primitive(TypeKind.FLOAT)).empty
        LiteralCoercion.coerce('Infinity', primitive(TypeKind.FLOAT)).empty
    }

    def 'float rejects text that does not parse as a float'() {
        expect:
        LiteralCoercion.coerce('not-a-float', primitive(TypeKind.FLOAT)).empty
    }

    def 'double coerces to a bare literal'() {
        expect:
        render(primitive(TypeKind.DOUBLE), '1.5') == '1.5'
    }

    def 'double rejects non-finite input'() {
        expect:
        LiteralCoercion.coerce('NaN', primitive(TypeKind.DOUBLE)).empty
        LiteralCoercion.coerce('-Infinity', primitive(TypeKind.DOUBLE)).empty
    }

    def 'double rejects text that does not parse as a double'() {
        expect:
        LiteralCoercion.coerce('not-a-double', primitive(TypeKind.DOUBLE)).empty
    }

    def 'a wrapper target coerces like its primitive, as a wrapper-typed expression'() {
        expect:
        render(declared('java.lang.Integer'), '7') == 'java.lang.Integer.valueOf(7)'
        render(declared('java.lang.Boolean'), 'true') == 'java.lang.Boolean.valueOf(true)'
        render(declared('java.lang.Byte'), '7') == 'java.lang.Byte.valueOf((byte) 7)'
        render(declared('java.lang.Short'), '7') == 'java.lang.Short.valueOf((short) 7)'
        render(declared('java.lang.Long'), '7') == 'java.lang.Long.valueOf(7L)'
        render(declared('java.lang.Character'), 'a') == "java.lang.Character.valueOf('a')"
        render(declared('java.lang.Float'), '1.5') == 'java.lang.Float.valueOf(1.5f)'
        render(declared('java.lang.Double'), '1.5') == 'java.lang.Double.valueOf(1.5)'
    }

    def 'a wrapper target fails coercion when its underlying primitive coercion fails'() {
        expect:
        LiteralCoercion.coerce('not-a-number', declared('java.lang.Integer')).empty
    }

    def 'an out-of-scope (enum) target fails coercion'() {
        expect:
        LiteralCoercion.coerce('MONDAY', declared('java.time.DayOfWeek')).empty
    }

    def 'a declared target whose element is not a TypeElement fails coercion'() {
        DeclaredType type = Mock()
        type.kind >> TypeKind.DECLARED
        type.asElement() >> Mock(Element)

        expect:
        LiteralCoercion.coerce('x', type).empty
    }

    def 'a non-primitive, non-declared target fails coercion'() {
        TypeMirror type = Mock()
        type.kind >> TypeKind.TYPEVAR

        expect:
        LiteralCoercion.coerce('x', type).empty
    }

    def 'char accepts exactly one character'() {
        expect:
        render(primitive(TypeKind.CHAR), 'a') == "'a'"
    }

    def 'char rejects multi-character strings'() {
        expect:
        LiteralCoercion.coerce('AB', primitive(TypeKind.CHAR)).empty
    }

    def 'char rejects the empty string'() {
        expect:
        LiteralCoercion.coerce('', primitive(TypeKind.CHAR)).empty
    }

    def 'char escapes the special Java escape characters'() {
        expect:
        render(primitive(TypeKind.CHAR), "'") == "'\\''"
        render(primitive(TypeKind.CHAR), '\\') == "'\\\\'"
        render(primitive(TypeKind.CHAR), '\n') == "'\\n'"
        render(primitive(TypeKind.CHAR), '\r') == "'\\r'"
        render(primitive(TypeKind.CHAR), '\t') == "'\\t'"
        render(primitive(TypeKind.CHAR), '\b') == "'\\b'"
        render(primitive(TypeKind.CHAR), '\f') == "'\\f'"
    }

    def 'char renders the printable-range boundaries verbatim'() {
        expect:
        render(primitive(TypeKind.CHAR), ' ') == "' '"
        render(primitive(TypeKind.CHAR), '~') == "'~'"
    }

    def 'char unicode-escapes a character just outside the printable range on either side'() {
        expect:
        render(primitive(TypeKind.CHAR), String.valueOf((char) 0x1f)) == "'\\u001f'"
        render(primitive(TypeKind.CHAR), String.valueOf((char) 0x7f)) == "'\\u007f'"
    }

    def 'boolean accepts only canonical true/false'() {
        expect:
        render(primitive(TypeKind.BOOLEAN), 'true') == 'true'
        render(primitive(TypeKind.BOOLEAN), 'false') == 'false'
    }

    def 'boolean rejects non-canonical text'() {
        expect:
        LiteralCoercion.coerce('yes', primitive(TypeKind.BOOLEAN)).empty
        LiteralCoercion.coerce('True', primitive(TypeKind.BOOLEAN)).empty
    }

    def 'numeric overflow fails rather than truncating'() {
        expect:
        LiteralCoercion.coerce('999', primitive(TypeKind.BYTE)).empty
    }

    def 'the raw string is not whitespace-trimmed'() {
        expect:
        LiteralCoercion.coerce(' 7', primitive(TypeKind.INT)).empty
        LiteralCoercion.coerce(' true', primitive(TypeKind.BOOLEAN)).empty
        LiteralCoercion.coerce(' hello ', declared('java.lang.String')).present
    }

    private String render(final TypeMirror type, final String raw) {
        LiteralCoercion.coerce(raw, type).orElseThrow().toString()
    }

    private TypeMirror primitive(final TypeKind kind) {
        TypeMirror type = Mock()
        type.kind >> kind
        type
    }

    private DeclaredType declared(final String fqn) {
        Name qualifiedName = Mock()
        qualifiedName.toString() >> fqn
        TypeElement element = Mock()
        element.qualifiedName >> qualifiedName
        DeclaredType type = Mock()
        type.kind >> TypeKind.DECLARED
        type.asElement() >> element
        type
    }
}
