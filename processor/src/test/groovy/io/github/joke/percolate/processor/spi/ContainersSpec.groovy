package io.github.joke.percolate.processor.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ContainersSpec extends Specification {

    def 'isOptional matches parameterised Optional'() {
        given:
        def ctx = mockCtx('java.util.Optional', true)

        expect:
        Containers.isOptional(mockDeclaredType(), ctx)
    }

    def 'isOptional matches raw Optional'() {
        given:
        def ctx = mockCtx('java.util.Optional', true)

        expect:
        Containers.isOptional(mockDeclaredType(), ctx)
    }

    def 'isOptional returns false for non-Optional'() {
        given:
        def ctx = mockCtx('java.util.Optional', false)

        expect:
        !Containers.isOptional(mockDeclaredType(), ctx)
    }

    def 'isOptional returns false for non-declared type'() {
        given:
        def ctx = Mock(ResolveCtx)
        def t = Mock(TypeMirror)
        t.kind >> TypeKind.INT

        expect:
        !Containers.isOptional(t, ctx)
    }

    def 'isList matches List'() {
        given:
        def ctx = mockCtx('java.util.List', true)

        expect:
        Containers.isList(mockDeclaredType(), ctx)
    }

    def 'typeArgument extracts element type'() {
        given:
        def ctx = Mock(ResolveCtx)
        def innerType = Mock(TypeMirror)
        def dt = Mock(DeclaredType)
        dt.kind >> TypeKind.DECLARED
        dt.getTypeArguments() >> [innerType]

        expect:
        Containers.typeArgument(dt, 0) == innerType
    }

    def 'typeArgument throws on non-declared type'() {
        given:
        def t = Mock(TypeMirror)
        t.kind >> TypeKind.INT

        when:
        Containers.typeArgument(t, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def 'isArray matches array type'() {
        expect:
        Containers.isArray(mockArrayType())
    }

    def 'isArray returns false for non-array'() {
        expect:
        !Containers.isArray(mockDeclaredType())
    }

    def 'arrayComponentType extracts component type'() {
        given:
        def componentType = Mock(TypeMirror)
        def arrayType = Mock(ArrayType)
        arrayType.kind >> TypeKind.ARRAY
        arrayType.getComponentType() >> componentType

        expect:
        Containers.arrayComponentType(arrayType) == componentType
    }

    def 'arrayComponentType throws on non-array'() {
        given:
        def t = Mock(TypeMirror)
        t.kind >> TypeKind.DECLARED

        when:
        Containers.arrayComponentType(t)

        then:
        thrown(IllegalArgumentException)
    }

    def 'isIterable matches List'() {
        given:
        def types = Mock(Types)
        def elements = Mock(Elements)
        def iterableTypeElement = Mock(TypeElement)
        def iterableDeclaredType = Mock(DeclaredType)
        iterableDeclaredType.kind >> TypeKind.DECLARED
        iterableTypeElement.asType() >> iterableDeclaredType
        elements.getTypeElement('java.lang.Iterable') >> iterableTypeElement
        types.erasure(_) >> iterableDeclaredType
        types.isAssignable(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        expect:
        Containers.isIterable(mockDeclaredType(), ctx)
    }

    def 'isIterable returns false for array'() {
        given:
        def ctx = Mock(ResolveCtx)
        def t = Mock(ArrayType)
        t.kind >> TypeKind.ARRAY

        expect:
        !Containers.isIterable(t, ctx)
    }

    private ResolveCtx mockCtx(String fqn, boolean matches) {
        def types = Mock(Types)
        def elements = Mock(Elements)
        def typeElement = Mock(TypeElement)
        def declaredType = Mock(DeclaredType)
        declaredType.kind >> TypeKind.DECLARED
        typeElement.asType() >> declaredType
        def erased = Mock(DeclaredType)
        erased.kind >> TypeKind.DECLARED
        elements.getTypeElement(fqn) >> typeElement
        types.erasure(_) >> erased
        types.isSameType(_, _) >> matches
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements
        ctx
    }

    private DeclaredType mockDeclaredType() {
        def dt = Mock(DeclaredType)
        dt.kind >> TypeKind.DECLARED
        dt
    }

    private ArrayType mockArrayType() {
        def at = Mock(ArrayType)
        at.kind >> TypeKind.ARRAY
        at
    }
}
