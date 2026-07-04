package io.github.joke.percolate.spi.types

import com.palantir.javapoet.TypeName
import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.test.fixtures.Outer
import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.array
import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.primitive
import static io.github.joke.percolate.spi.types.TypeRef.variable

/**
 * Golden comparison of the {@code TypeRef → TypeName} emitter against JavaPoet's own mirror-based rendering
 * ({@code TypeName.get(TypeMirror)} over the still-alive {@code TypeUniverse} javac substrate).
 *
 * <p><b>Scope (change {@code evict-javax-model}, design D7 amendment):</b> the emitter is safe only where the
 * source {@link TypeRef} is guaranteed wildcard-free — v1's model has no wildcard representation (design D3),
 * so a wildcard-bearing type would silently render its upper bound instead, which is <em>not</em> behaviour-
 * preserving anywhere the rendering must satisfy Java's exact-match rules (a method override signature, a
 * hoisted local's declared type). Those sites (all of {@code AssembleMapperType}, and {@code BuildMethodBodies}'
 * local-declaration site) stay on {@code TypeName.get(TypeMirror)} permanently — they are "faithful mirror of
 * the source" work, not engine currency, so the module-boundary confinement rule (design D6) exempts them. The
 * emitter is used only where the type is known wildcard-free by construction (e.g. a bare class name never
 * carrying arguments, as in {@code ConstructorCall}'s {@code new $T(...)}).
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

    def 'a bare nested class name resolves via bestGuess exactly like ClassName.get(TypeElement)'() {
        expect:
        TypeNames.toTypeName(declared('io.github.joke.percolate.spi.types.test.fixtures.Outer.Inner')).toString() ==
                TypeName.get(TypeUniverse.of(Outer.Inner).asType()).toString()
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
