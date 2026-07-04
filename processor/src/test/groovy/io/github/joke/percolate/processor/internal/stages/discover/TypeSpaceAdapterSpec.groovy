package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.discover.Greeting
import io.github.joke.percolate.processor.discover.PersonDto
import io.github.joke.percolate.processor.discover.PersonNode
import io.github.joke.percolate.processor.discover.Season
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.DeclKind
import io.github.joke.percolate.spi.types.MemberFlag
import io.github.joke.percolate.spi.types.PrimitiveKind
import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.array
import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.primitive

/**
 * Task 3.1 — the discovery adapter reads real {@code javax.lang.model} (via the shared javac substrate) into an
 * owned {@code TypeSpace}. Proves the declared-type closure walk, the edge-only JDK bound, member mirroring of a
 * non-JDK DTO, and that the produced snapshot answers the algebra over javac-derived supertype edges.
 */
@Tag('unit')
class TypeSpaceAdapterSpec extends Specification {

    static final NullabilityResolver UNKNOWN = { type, scope -> Nullability.UNKNOWN } as NullabilityResolver

    def adapter = new TypeSpaceAdapter(UNKNOWN)

    def 'materialises the declared closure of a JDK root, edge-only, and answers assignability over its edges'() {
        def space = adapter.build([TypeUniverse.LIST_OF_STRING])

        expect: 'the root and its reachable supertypes and type argument are declared'
        space.decl('java.util.List').present
        space.decl('java.util.List').get().kind == DeclKind.INTERFACE
        space.decl('java.util.List').get().typeParameters == ['E']
        space.decl('java.lang.String').present

        and: 'a JDK type is edge-only — supertype edges without member enumeration'
        space.decl('java.util.List').get().methods.empty
        !space.decl('java.util.List').get().superEdges.empty

        and: 'assignability walks the javac-derived edges with type-argument substitution'
        space.isAssignable(
                declared('java.util.List', declared('java.lang.String')),
                declared('java.lang.Iterable', declared('java.lang.String')))
    }

    def 'mirrors a non-JDK type\'s declared methods, fields, and constructor with resolved return types'() {
        def space = adapter.build([TypeUniverse.of(PersonDto).asType()])
        def decl = space.decl('io.github.joke.percolate.processor.discover.PersonDto').get()

        expect:
        decl.kind == DeclKind.CLASS

        and:
        def getName = decl.methods.find { it.name == 'getName' }
        getName.returnType == declared('java.lang.String')
        getName.has(MemberFlag.PUBLIC)

        and:
        def getAge = decl.methods.find { it.name == 'getAge' }
        getAge.returnType == primitive(PrimitiveKind.INT)

        and:
        def nameField = decl.fields.find { it.name == 'name' }
        nameField.type == declared('java.lang.String')
        nameField.has(MemberFlag.PRIVATE)

        and: 'the constructor is a MethodSig flagged CONSTRUCTOR carrying its parameter types'
        def constructor = decl.methods.find { it.has(MemberFlag.CONSTRUCTOR) }
        constructor.name == '<init>'
        constructor.parameterTypes() == [declared('java.lang.String'), primitive(PrimitiveKind.INT)]
    }

    def 'the closure walk is cycle-safe over a self-referencing type and materialises array field types'() {
        def space = adapter.build([TypeUniverse.of(PersonNode).asType()])
        def decl = space.decl('io.github.joke.percolate.processor.discover.PersonNode').get()

        expect: 'the self-referencing type is visited once, not walked forever'
        space.decl('io.github.joke.percolate.processor.discover.PersonNode').present

        and: 'an array field of the type itself is materialised as an array of the declared component'
        def children = decl.fields.find { it.name == 'children' }
        children.type == array(declared('io.github.joke.percolate.processor.discover.PersonNode'))
    }

    def 'mirrors a non-JDK interface\'s static, default, and abstract methods'() {
        def space = adapter.build([TypeUniverse.of(Greeting).asType()])
        def decl = space.decl('io.github.joke.percolate.processor.discover.Greeting').get()

        expect:
        decl.kind == DeclKind.INTERFACE

        and:
        decl.methods.find { it.name == 'of' }.has(MemberFlag.STATIC)

        and:
        decl.methods.find { it.name == 'shout' }.has(MemberFlag.DEFAULT)

        and:
        decl.methods.find { it.name == 'text' }.has(MemberFlag.ABSTRACT)
    }

    def 'recognises an enum declaration kind'() {
        def space = adapter.build([TypeUniverse.of(Season).asType()])

        expect:
        space.decl('io.github.joke.percolate.processor.discover.Season').get().kind == DeclKind.ENUM
    }
}
