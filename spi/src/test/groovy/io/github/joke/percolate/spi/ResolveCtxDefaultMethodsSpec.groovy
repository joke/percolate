package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * {@link ResolveCtxSpec} pins the seam's higher-level default-method <b>composition</b> against a
 * {@link io.github.joke.percolate.spi.test.FakeResolveCtx} that overrides every leaf type-algebra/member-reflection
 * method structurally — so those leaf methods' own bodies (the ones that actually call {@link ResolveCtx#types()} /
 * {@link ResolveCtx#elements()}) never run there. This spec closes that gap: {@link LeafDefaultsCtx} implements only
 * the four abstract members ({@code types}/{@code elements}/{@code callableMethods}/{@code configuredTimeZone}),
 * leaving every other {@link ResolveCtx} method as the interface's own default body, so each leaf default is
 * exercised for real against a mocked {@link Types}/{@link Elements} pair. No javac.
 */
@Tag('unit')
class ResolveCtxDefaultMethodsSpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    ResolveCtx ctx = new LeafDefaultsCtx(types, elements)
    TypeMirror a = Mock()
    TypeMirror b = Mock()

    def 'isSameType delegates to Types.isSameType, true case'() {
        when:
        def result = ctx.isSameType(a, b)

        then:
        1 * types.isSameType(a, b) >> true
        0 * _

        expect:
        result
    }

    def 'isSameType delegates to Types.isSameType, false case'() {
        when:
        def result = ctx.isSameType(a, b)

        then:
        1 * types.isSameType(a, b) >> false
        0 * _

        expect:
        !result
    }

    def 'isAssignable delegates to Types.isAssignable, true case'() {
        when:
        def result = ctx.isAssignable(a, b)

        then:
        1 * types.isAssignable(a, b) >> true
        0 * _

        expect:
        result
    }

    def 'isAssignable delegates to Types.isAssignable, false case'() {
        when:
        def result = ctx.isAssignable(a, b)

        then:
        1 * types.isAssignable(a, b) >> false
        0 * _

        expect:
        !result
    }

    def 'erasure delegates to Types.erasure'() {
        TypeMirror erased = Mock()

        when:
        def result = ctx.erasure(a)

        then:
        1 * types.erasure(a) >> erased
        0 * _

        expect:
        result.is(erased)
    }

    def 'kind reads the raw TypeKind, without consulting Types/Elements'() {
        a.kind >> TypeKind.INT

        expect:
        ctx.kind(a) == TypeKind.INT
    }

    def 'isPrimitive/isArray/isDeclared/isTypeVariable classify a type, without consulting Types/Elements'() {
        expect:
        ctx.isPrimitive(kindOf(TypeKind.INT))
        !ctx.isPrimitive(kindOf(TypeKind.DECLARED))
        ctx.isArray(kindOf(TypeKind.ARRAY))
        !ctx.isArray(kindOf(TypeKind.DECLARED))
        ctx.isDeclared(kindOf(TypeKind.DECLARED))
        !ctx.isDeclared(kindOf(TypeKind.ARRAY))
        ctx.isTypeVariable(kindOf(TypeKind.TYPEVAR))
        !ctx.isTypeVariable(kindOf(TypeKind.DECLARED))
    }

    def 'typeArgument reads the requested type argument of a declared type'() {
        TypeMirror first = Mock()
        TypeMirror second = Mock()
        DeclaredType declared = Mock()
        declared.kind >> TypeKind.DECLARED
        declared.typeArguments >> [first, second]

        expect:
        ctx.typeArgument(declared, 0).is(first)
        ctx.typeArgument(declared, 1).is(second)
    }

    def 'typeArgument throws on a non-declared type'() {
        when:
        ctx.typeArgument(kindOf(TypeKind.INT), 0)

        then:
        thrown(IllegalArgumentException)
    }

    def 'typeArgument throws on an out-of-bounds index'() {
        DeclaredType declared = Mock()
        declared.kind >> TypeKind.DECLARED
        declared.typeArguments >> [Mock(TypeMirror)]

        when:
        ctx.typeArgument(declared, 5)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'typeArgumentCount reads the number of type arguments of a declared type'() {
        DeclaredType declared = Mock()
        declared.kind >> TypeKind.DECLARED
        declared.typeArguments >> [Mock(TypeMirror), Mock(TypeMirror)]

        expect:
        ctx.typeArgumentCount(declared) == 2
    }

    def 'typeArgumentCount throws on a non-declared type'() {
        when:
        ctx.typeArgumentCount(kindOf(TypeKind.INT))

        then:
        thrown(IllegalArgumentException)
    }

    def 'arrayComponent reads the component type of an array'() {
        TypeMirror component = Mock()
        ArrayType array = Mock()
        array.kind >> TypeKind.ARRAY
        array.componentType >> component

        expect:
        ctx.arrayComponent(array).is(component)
    }

    def 'arrayComponent throws on a non-array type'() {
        when:
        ctx.arrayComponent(kindOf(TypeKind.DECLARED))

        then:
        thrown(IllegalArgumentException)
    }

    def 'declaredType delegates to Types.getDeclaredType'() {
        TypeElement element = Stub()
        DeclaredType result = Stub()

        when:
        def actual = ctx.declaredType(element, a, b)

        then:
        1 * types.getDeclaredType(element, a, b) >> result
        0 * _

        expect:
        actual.is(result)
    }

    def 'arrayType delegates to Types.getArrayType'() {
        ArrayType result = Stub()

        when:
        def actual = ctx.arrayType(a)

        then:
        1 * types.getArrayType(a) >> result
        0 * _

        expect:
        actual.is(result)
    }

    def 'boxed delegates to Types.boxedClass, unwrapping the element to its type'() {
        PrimitiveType primitive = Stub()
        TypeMirror wrapperType = Stub()
        TypeElement wrapperElement = Stub()
        wrapperElement.asType() >> wrapperType

        when:
        def actual = ctx.boxed(primitive)

        then:
        1 * types.boxedClass(primitive) >> wrapperElement
        0 * _

        expect:
        actual.is(wrapperType)
    }

    def 'unboxed delegates to Types.unboxedType'() {
        TypeMirror wrapper = Stub()
        PrimitiveType primitive = Stub()

        when:
        def actual = ctx.unboxed(wrapper)

        then:
        1 * types.unboxedType(wrapper) >> primitive
        0 * _

        expect:
        actual.is(primitive)
    }

    def 'primitiveType delegates to Types.getPrimitiveType'() {
        PrimitiveType result = Stub()

        when:
        def actual = ctx.primitiveType(TypeKind.LONG)

        then:
        1 * types.getPrimitiveType(TypeKind.LONG) >> result
        0 * _

        expect:
        actual.is(result)
    }

    def 'simpleName reads a declared type\'s simple element name'() {
        DeclaredType declared = Stub()
        declared.kind >> TypeKind.DECLARED
        TypeElement element = Stub()
        Name simpleName = Stub()
        simpleName.toString() >> 'String'
        element.simpleName >> simpleName

        when:
        def result = ctx.simpleName(declared)

        then:
        1 * types.asElement(declared) >> element
        0 * _

        expect:
        result == 'String'
    }

    def 'simpleName falls back to the text form for a non-declared type'() {
        TypeMirror type = Stub()
        type.kind >> TypeKind.INT
        type.toString() >> 'int'

        expect:
        ctx.simpleName(type) == 'int'
    }

    def 'qualifiedName reads a declared type\'s fully-qualified element name'() {
        DeclaredType declared = Stub()
        declared.kind >> TypeKind.DECLARED
        TypeElement element = Stub()
        Name qualifiedName = Stub()
        qualifiedName.toString() >> 'java.lang.String'
        element.qualifiedName >> qualifiedName

        when:
        def result = ctx.qualifiedName(declared)

        then:
        1 * types.asElement(declared) >> element
        0 * _

        expect:
        result == 'java.lang.String'
    }

    def 'qualifiedName falls back to the text form for a non-declared type'() {
        TypeMirror type = Stub()
        type.kind >> TypeKind.INT
        type.toString() >> 'int'

        expect:
        ctx.qualifiedName(type) == 'int'
    }

    def 'asTypeElement resolves the backing TypeElement of a declared type'() {
        DeclaredType declared = Stub()
        declared.kind >> TypeKind.DECLARED
        TypeElement element = Stub()

        when:
        def result = ctx.asTypeElement(declared)

        then:
        1 * types.asElement(declared) >> element
        0 * _

        expect:
        result.present
        result.get().is(element)
    }

    def 'asTypeElement is empty when the declared element is not a TypeElement'() {
        DeclaredType declared = Stub()
        declared.kind >> TypeKind.DECLARED
        Element nonTypeElement = Stub()

        when:
        def result = ctx.asTypeElement(declared)

        then:
        1 * types.asElement(declared) >> nonTypeElement
        0 * _

        expect:
        !result.present
    }

    def 'asTypeElement is empty for a non-declared type, without consulting Types'() {
        when:
        def result = ctx.asTypeElement(kindOf(TypeKind.INT))

        then:
        0 * _

        expect:
        !result.present
    }

    def 'typeElementNamed delegates to Elements.getTypeElement'() {
        TypeElement element = Stub()

        when:
        def result = ctx.typeElementNamed('java.lang.String')

        then:
        1 * elements.getTypeElement('java.lang.String') >> element
        0 * _

        expect:
        result.is(element)
    }

    def 'superclassOf reads the direct superclass of a declared type\'s element'() {
        DeclaredType declared = Stub()
        declared.kind >> TypeKind.DECLARED
        TypeMirror superclass = Stub()
        TypeElement element = Stub()
        element.superclass >> superclass

        when:
        def result = ctx.superclassOf(declared)

        then:
        1 * types.asElement(declared) >> element
        0 * _

        expect:
        result.is(superclass)
    }

    def 'superclassOf falls back to Types.getNoType(NONE) when there is no backing element'() {
        NoType none = Stub()

        when:
        def result = ctx.superclassOf(kindOf(TypeKind.INT))

        then:
        1 * types.getNoType(TypeKind.NONE) >> none
        0 * _

        expect:
        result.is(none)
    }

    def 'membersOf delegates to Elements.getAllMembers'() {
        TypeElement parent = Mock()
        Element member = Mock()

        when:
        def result = ctx.membersOf(parent).toList()

        then:
        1 * elements.getAllMembers(parent) >> [member]
        0 * _

        expect:
        result == [member]
    }

    private TypeMirror kindOf(final TypeKind kind) {
        TypeMirror type = Stub()
        type.kind >> kind
        type
    }

    /** Implements only the four abstract {@link ResolveCtx} members, leaving every other method as the real default. */
    private static class LeafDefaultsCtx implements ResolveCtx {
        private final Types typesDelegate
        private final Elements elementsDelegate

        LeafDefaultsCtx(final Types types, final Elements elements) {
            this.typesDelegate = types
            this.elementsDelegate = elements
        }

        @Override
        Types types() {
            typesDelegate
        }

        @Override
        Elements elements() {
            elementsDelegate
        }

        @Override
        CallableMethods callableMethods() {
            null
        }

        @Override
        Optional<String> configuredTimeZone() {
            Optional.empty()
        }
    }
}
