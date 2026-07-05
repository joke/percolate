package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

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

    def 'long renders with the L suffix'() {
        expect:
        render(primitive(TypeKind.LONG), '42') == '42L'
    }

    def 'a wrapper target coerces like its primitive, as a wrapper-typed expression'() {
        expect:
        render(declared('java.lang.Integer'), '7') == 'java.lang.Integer.valueOf(7)'
    }

    def 'an out-of-scope (enum) target fails coercion'() {
        expect:
        LiteralCoercion.coerce('MONDAY', declared('java.time.DayOfWeek')).empty
    }

    def 'char accepts exactly one character'() {
        expect:
        render(primitive(TypeKind.CHAR), 'a') == "'a'"
    }

    def 'char rejects multi-character strings'() {
        expect:
        LiteralCoercion.coerce('AB', primitive(TypeKind.CHAR)).empty
    }

    def 'boolean accepts only canonical true/false'() {
        expect:
        render(primitive(TypeKind.BOOLEAN), 'true') == 'true'
    }

    def 'boolean rejects non-canonical text'() {
        expect:
        LiteralCoercion.coerce('yes', primitive(TypeKind.BOOLEAN)).empty
    }

    def 'numeric overflow fails rather than truncating'() {
        expect:
        LiteralCoercion.coerce('999', primitive(TypeKind.BYTE)).empty
    }

    def 'the raw string is not whitespace-trimmed'() {
        expect:
        LiteralCoercion.coerce(' 7', primitive(TypeKind.INT)).empty
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
