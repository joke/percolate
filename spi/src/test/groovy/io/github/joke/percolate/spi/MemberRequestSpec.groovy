package io.github.joke.percolate.spi

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.lib.javapoet.TypeName
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link MemberRequest} is the pseudo-sealed class-member request a strategy attaches to an {@link OperationSpec}
 * (design D2 of change {@code add-setter-assembly}): a {@link MemberRequest.Field} initialized once, or a
 * {@link MemberRequest.Method} carrying a statement body. Each leaf is a Lombok {@code @Value}, so equality is
 * structural, and both expose the dedup key the generate stage deduplicates by.
 */
@Tag('unit')
class MemberRequestSpec extends Specification {

    CodeBlock initializer = CodeBlock.of('$L', 0)
    CodeBlock body = CodeBlock.of('return null;')
    def parameter = new MemberRequest.Parameter(TypeName.INT, 'age')

    def 'field wraps the given type, initializer and dedup key in a Field'() {
        expect:
        MemberRequest.field(TypeName.INT, initializer, 'key') ==
                new MemberRequest.Field(TypeName.INT, initializer, 'key')
    }

    def 'method wraps the given hint, return type, parameters, body and dedup key in a Method'() {
        expect:
        MemberRequest.method('assemblePerson', TypeName.INT, [parameter], body, 'key') ==
                new MemberRequest.Method('assemblePerson', TypeName.INT, [parameter], body, 'key')
    }

    def 'a Field exposes its dedup key through the base type'() {
        MemberRequest request = MemberRequest.field(TypeName.INT, initializer, 'fmt-yyyy')

        expect:
        request.dedupKey == 'fmt-yyyy'
    }

    def 'a Method exposes its dedup key through the base type'() {
        MemberRequest request = MemberRequest.method('assemblePerson', TypeName.INT, [parameter], body, 'setter:Person:age')

        expect:
        request.dedupKey == 'setter:Person:age'
    }

    def 'a Method preserves parameter order'() {
        def name = new MemberRequest.Parameter(TypeName.get(String), 'name')

        expect:
        MemberRequest.method('assemblePerson', TypeName.INT, [name, parameter], body, 'key').parameters ==
                [name, parameter]
    }

    def 'a Method copies its parameter list defensively'() {
        def parameters = [parameter]
        def request = MemberRequest.method('assemblePerson', TypeName.INT, parameters, body, 'key')
        parameters.clear()

        expect:
        request.parameters == [parameter]
    }

    def 'two Field instances over the same content are equal; over different content are not'() {
        expect:
        MemberRequest.field(TypeName.INT, initializer, 'key') == MemberRequest.field(TypeName.INT, initializer, 'key')
        MemberRequest.field(TypeName.INT, initializer, 'key') != MemberRequest.field(TypeName.LONG, initializer, 'key')
        MemberRequest.field(TypeName.INT, initializer, 'key') != MemberRequest.field(TypeName.INT, body, 'key')
        MemberRequest.field(TypeName.INT, initializer, 'key') != MemberRequest.field(TypeName.INT, initializer, 'other')
    }

    def 'two Method instances over the same content are equal; over different content are not'() {
        def other = new MemberRequest.Parameter(TypeName.LONG, 'id')

        expect:
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') ==
                MemberRequest.method('a', TypeName.INT, [parameter], body, 'key')
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') !=
                MemberRequest.method('b', TypeName.INT, [parameter], body, 'key')
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') !=
                MemberRequest.method('a', TypeName.LONG, [parameter], body, 'key')
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') !=
                MemberRequest.method('a', TypeName.INT, [other], body, 'key')
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') !=
                MemberRequest.method('a', TypeName.INT, [parameter], initializer, 'key')
        MemberRequest.method('a', TypeName.INT, [parameter], body, 'key') !=
                MemberRequest.method('a', TypeName.INT, [parameter], body, 'other')
    }

    def 'a Method renders its content, and equal Methods share a hash code'() {
        def request = MemberRequest.method('assemblePerson', TypeName.INT, [parameter], body, 'key')

        expect:
        request.toString().contains('assemblePerson')
        request.hashCode() == MemberRequest.method('assemblePerson', TypeName.INT, [parameter], body, 'key').hashCode()
    }

    def 'a Field renders its content, and equal Fields share a hash code'() {
        def request = MemberRequest.field(TypeName.INT, initializer, 'key')

        expect:
        request.toString().contains('key')
        request.hashCode() == MemberRequest.field(TypeName.INT, initializer, 'key').hashCode()
    }

    def 'a Field never equals a Method'() {
        expect:
        MemberRequest.field(TypeName.INT, initializer, 'key') !=
                MemberRequest.method('a', TypeName.INT, [], initializer, 'key')
    }

    def 'a Parameter exposes its type and name'() {
        expect:
        parameter.type == TypeName.INT
        parameter.name == 'age'
    }

    def 'two Parameter instances over the same type and name are equal; over different ones are not'() {
        expect:
        new MemberRequest.Parameter(TypeName.INT, 'age') == new MemberRequest.Parameter(TypeName.INT, 'age')
        new MemberRequest.Parameter(TypeName.INT, 'age') != new MemberRequest.Parameter(TypeName.LONG, 'age')
        new MemberRequest.Parameter(TypeName.INT, 'age') != new MemberRequest.Parameter(TypeName.INT, 'id')
    }
}
