package io.github.joke.percolate.spi.types.test

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.types.DeclKind
import io.github.joke.percolate.spi.types.FieldSig
import io.github.joke.percolate.spi.types.MemberFlag
import io.github.joke.percolate.spi.types.MethodSig
import io.github.joke.percolate.spi.types.Origin
import io.github.joke.percolate.spi.types.PrimitiveKind
import io.github.joke.percolate.spi.types.TypeDecl
import io.github.joke.percolate.spi.types.TypeSpace
import io.github.joke.percolate.spi.types.test.fixtures.Box
import io.github.joke.percolate.spi.types.test.fixtures.PersonBean
import io.github.joke.percolate.spi.types.test.fixtures.StringBox
import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.primitive
import static io.github.joke.percolate.spi.types.TypeRef.variable

/**
 * Task 2.6 — unit tests for the {@code spi} testFixtures (design D8): the reflection mirror against compiled
 * fixture classes, the literal-builder construction path, and the prebuilt constants. All javac-free and
 * parallel-safe by construction (plain values, no shared state).
 */
@Tag('unit')
class TestTypesSpec extends Specification {

    def 'mirrors a bean class\'s declared members'() {
        def decl = TestTypes.of(PersonBean)

        expect:
        decl.qualifiedName == 'io.github.joke.percolate.spi.types.test.fixtures.PersonBean'
        decl.kind == DeclKind.CLASS
        decl.typeParameters.empty

        and:
        def name = decl.methods.find { it.name == 'getName' }
        name.parameters.empty
        name.returnType == declared('java.lang.String')
        name.has(MemberFlag.PUBLIC)

        and:
        def age = decl.methods.find { it.name == 'getAge' }
        age.returnType == primitive(PrimitiveKind.INT)

        and:
        def nameField = decl.fields.find { it.name == 'name' }
        nameField.type == declared('java.lang.String')
        nameField.has(MemberFlag.PRIVATE)
    }

    def 'mirrors declared constructors flagged as constructors'() {
        def constructors = TestTypes.constructorsOf(PersonBean)

        expect:
        constructors.size() == 1
        constructors[0].name == '<init>'
        constructors[0].has(MemberFlag.CONSTRUCTOR)
        constructors[0].parameterTypes() == [declared('java.lang.String'), primitive(PrimitiveKind.INT)]
    }

    def 'mirrors a generic class\'s type parameters'() {
        def decl = TestTypes.of(Box)

        expect:
        decl.typeParameters == ['T']

        and:
        def value = decl.methods.find { it.name == 'value' }
        value.returnType == variable('T')
    }

    def 'mirrors a generic supertype edge with its actual type argument'() {
        def decl = TestTypes.of(StringBox)

        expect:
        decl.superEdges == [declared('io.github.joke.percolate.spi.types.test.fixtures.Box', declared('java.lang.String'))]
    }

    def 'a spec builds a space inline through the literal builder'() {
        def numbered = declared('com.acme.Numbered', variable('T'))
        def decl = TypeDecl.of('com.acme.Box', ['T'], [numbered])
        def space = TypeSpace.of(decl)

        expect:
        space.isAssignable(declared('com.acme.Box', TestTypes.STRING), declared('com.acme.Numbered', TestTypes.STRING))
        space.erasure(declared('com.acme.Box', TestTypes.STRING)) == declared('com.acme.Box')
    }

    def 'the members-inclusive literal builder attaches methods and fields'() {
        def method = MethodSig.of('getName', TestTypes.STRING)
        def field = new FieldSig('name', 'com.acme.Person', TestTypes.STRING, Nullability.UNKNOWN, [MemberFlag.PRIVATE] as Set, Origin.none())
        def decl = TypeDecl.of('com.acme.Person', [], [], [method], [field])

        expect:
        decl.methods == [method]
        decl.fields == [field]
    }

    def 'constants are parallel-safe plain values'() {
        expect:
        first == second

        where:
        first                  | second
        TestTypes.STRING       | declared('java.lang.String')
        TestTypes.INTEGER      | declared('java.lang.Integer')
        TestTypes.INT          | primitive(PrimitiveKind.INT)
        TestTypes.LONG         | primitive(PrimitiveKind.LONG)
        TestTypes.LIST_OF_STRING | declared('java.util.List', declared('java.lang.String'))
        TestTypes.LIST_OF_INT  | declared('java.util.List', declared('java.lang.Integer'))
    }
}
