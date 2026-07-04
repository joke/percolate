package io.github.joke.percolate.spi.types

import spock.lang.Specification
import spock.lang.Tag

import static io.github.joke.percolate.spi.types.TypeRef.array
import static io.github.joke.percolate.spi.types.TypeRef.declared
import static io.github.joke.percolate.spi.types.TypeRef.primitive
import static io.github.joke.percolate.spi.types.TypeRef.variable

@Tag('unit')
class TypeSpaceSpec extends Specification {

    static final TypeRef STRING = declared('java.lang.String')
    static final TypeRef INTEGER = declared('java.lang.Integer')

    def collectionsSpace = TypeSpace.of(
            TypeDecl.of('java.util.ArrayList', ['E'], [declared('java.util.List', variable('E'))]),
            TypeDecl.of('java.util.List', ['E'], [declared('java.util.Collection', variable('E'))]),
            TypeDecl.of('java.util.Collection', ['E'], [declared('java.lang.Iterable', variable('E'))]))

    def 'independently constructed refs are the same type and share a map key'() {
        def first = declared('java.util.List', STRING)
        def second = declared('java.util.List', declared('java.lang.String'))
        def dedup = [(first): 'value']

        expect:
        collectionsSpace.isSameType(first, second)
        dedup[second] == 'value'
    }

    def 'argument-divergent refs are not the same type'() {
        expect:
        !collectionsSpace.isSameType(declared('java.util.List', STRING), declared('java.util.List', INTEGER))
    }

    def 'toString renders the source form'() {
        expect:
        ref.toString() == rendered

        where:
        ref                                                    | rendered
        STRING                                                 | 'java.lang.String'
        declared('java.util.List', STRING)                     | 'java.util.List<java.lang.String>'
        array(STRING)                                          | 'java.lang.String[]'
        primitive(PrimitiveKind.INT)                           | 'int'
        variable('A')                                          | 'A'
        declared('java.util.Map', STRING, variable('V'))       | 'java.util.Map<java.lang.String, V>'
    }

    def 'erasure drops type arguments and is idempotent'() {
        def listOfString = declared('java.util.List', STRING)
        def erased = collectionsSpace.erasure(listOfString)

        expect:
        erased == declared('java.util.List')
        collectionsSpace.erasure(erased) == erased
        collectionsSpace.erasure(array(listOfString)) == array(declared('java.util.List'))
        collectionsSpace.erasure(primitive(PrimitiveKind.INT)) == primitive(PrimitiveKind.INT)
    }

    def 'assignability walks declared edges with argument substitution'() {
        expect:
        collectionsSpace.isAssignable(from, to) == assignable

        where:
        from                                    | to                                        | assignable
        declared('java.util.List', STRING)      | declared('java.util.List', STRING)        | true
        declared('java.util.ArrayList', STRING) | declared('java.util.List', STRING)        | true
        declared('java.util.ArrayList', STRING) | declared('java.util.List', INTEGER)       | false
        declared('java.util.ArrayList', STRING) | declared('java.util.List')                | true
        declared('java.util.ArrayList', STRING) | declared('java.util.Collection', STRING)  | true
        declared('java.util.ArrayList', STRING) | declared('java.lang.Iterable', STRING)    | true
        declared('java.util.List', STRING)      | declared('java.util.ArrayList', STRING)   | false
        STRING                                  | declared('java.util.List', STRING)        | false
        declared('java.util.List', STRING)      | declared('java.util.List', variable('E')) | false
    }

    def 'primitive assignability is identity only'() {
        expect:
        collectionsSpace.isAssignable(primitive(PrimitiveKind.INT), primitive(PrimitiveKind.INT))
        !collectionsSpace.isAssignable(primitive(PrimitiveKind.INT), primitive(PrimitiveKind.LONG))
    }

    def 'boxing round-trips through the fixed table'() {
        def boxed = collectionsSpace.boxed(kind)

        expect:
        boxed == declared(wrapper)
        collectionsSpace.unboxed(boxed).get() == kind

        where:
        kind                  | wrapper
        PrimitiveKind.BOOLEAN | 'java.lang.Boolean'
        PrimitiveKind.BYTE    | 'java.lang.Byte'
        PrimitiveKind.SHORT   | 'java.lang.Short'
        PrimitiveKind.CHAR    | 'java.lang.Character'
        PrimitiveKind.INT     | 'java.lang.Integer'
        PrimitiveKind.LONG    | 'java.lang.Long'
        PrimitiveKind.FLOAT   | 'java.lang.Float'
        PrimitiveKind.DOUBLE  | 'java.lang.Double'
    }

    def 'unboxed of a non-wrapper is empty'() {
        expect:
        collectionsSpace.unboxed(STRING).empty
        collectionsSpace.unboxed(declared('java.lang.Integer', STRING)).empty
    }

    def 'two snapshots are independent values'() {
        def other = TypeSpace.of(
                TypeDecl.of('java.util.ArrayList', ['E'], []))

        expect:
        collectionsSpace.isAssignable(declared('java.util.ArrayList', STRING), declared('java.util.List', STRING))
        !other.isAssignable(declared('java.util.ArrayList', STRING), declared('java.util.List', STRING))
    }
}
