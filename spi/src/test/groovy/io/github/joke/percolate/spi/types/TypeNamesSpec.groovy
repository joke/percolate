package io.github.joke.percolate.spi.types

import com.palantir.javapoet.TypeName
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.array
import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.primitive
import static io.github.joke.percolate.spi.types.TypeRef.variable

/**
 * SPIKE task 1.4 — golden comparison of the {@code TypeRef → TypeName} emitter against JavaPoet's own
 * mirror-based rendering ({@code TypeName.get(TypeMirror)} over the still-alive {@code TypeUniverse} javac
 * substrate). The emitter must be indistinguishable from the mirror path for every shape codegen emits.
 */
@Tag('unit')
class TypeNamesSpec extends Specification {

    static arrayOfString() {
        TypeUniverse.types().getArrayType(TypeUniverse.STRING)
    }

    static optionalOfSetOfStringRef() {
        declared('java.util.Optional', declared('java.util.Set', declared('java.lang.String')))
    }

    static optionalOfSetOfStringMirror() {
        def types = TypeUniverse.types()
        def setOfString = types.getDeclaredType(TypeUniverse.element('java.util.Set'), TypeUniverse.STRING)
        types.getDeclaredType(TypeUniverse.element('java.util.Optional'), setOfString)
    }

    def 'emitter output matches JavaPoet mirror rendering'() {
        expect:
        TypeNames.toTypeName(ref).toString() == TypeName.get(mirror).toString()

        where:
        ref                                                | mirror
        declared('java.lang.String')                       | TypeUniverse.STRING
        declared('java.lang.Integer')                      | TypeUniverse.INTEGER
        primitive(PrimitiveKind.INT)                       | TypeUniverse.INT
        primitive(PrimitiveKind.LONG)                      | TypeUniverse.LONG
        declared('java.util.List', declared('java.lang.String'))  | TypeUniverse.LIST_OF_STRING
        array(declared('java.lang.String'))                | arrayOfString()
        optionalOfSetOfStringRef()                         | optionalOfSetOfStringMirror()
    }

    def 'a free type variable renders by name'() {
        expect:
        TypeNames.toTypeName(variable('A')).toString() == 'A'
    }

    def 'the none ref renders as void'() {
        expect:
        TypeNames.toTypeName(TypeRef.none()) == TypeName.VOID
    }
}
