package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.FakeName
import io.github.joke.percolate.spi.test.FakeResolveCtx
import io.github.joke.percolate.spi.test.FakeType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Pins the {@link ResolveCtx} type-query seam's own default-method composition (change {@code type-query-seam}):
 * {@code isType}/{@code isList}/{@code isCollection}/{@code isEnum}/{@code membersOf}-consumers and the rest are
 * real production logic, not just pass-through — this is the one spec that exercises them directly rather than
 * stubbing them away. Driven by a {@link FakeResolveCtx} over {@link FakeType}: the ~13 leaf type-algebra and
 * member-reflection methods are answered structurally (never by a compiler), while the higher-level defaults under
 * test run for real. No javac, no shared static substrate.
 */
@Tag('unit')
class ResolveCtxSpec extends Specification {

    @Shared TypeElement stringElement = Stub(TypeElement)
    @Shared TypeElement integerElement = Stub(TypeElement)
    @Shared TypeElement listElement = Stub(TypeElement)
    @Shared TypeElement optionalElement = Stub(TypeElement)
    @Shared TypeElement collectionElement = Stub(TypeElement)
    @Shared TypeElement iterableElement = Stub(TypeElement)
    @Shared TypeElement objectElement = Stub(TypeElement)
    @Shared TypeElement memberFixtureBaseElement = Stub(TypeElement)
    @Shared TypeElement memberFixtureElement = Stub(TypeElement)
    @Shared TypeElement dayOfWeekElement = Stub(TypeElement)

    @Shared Element fieldMember = Stub(Element)
    @Shared Element privateFieldMember = Stub(Element)
    @Shared Element staticFieldMember = Stub(Element)
    @Shared Element methodMember = Stub(Element)
    @Shared Element toStringMember = Stub(Element)
    @Shared Element ctorMember = Stub(Element)

    @Shared ResolveCtx ctx = new FakeResolveCtx()

    @Shared TypeMirror STRING = FakeType.declared(stringElement)
    @Shared TypeMirror INT = FakeType.marker(TypeKind.INT)
    @Shared TypeMirror INTEGER = FakeType.declared(integerElement)
    @Shared TypeMirror stringArray = FakeType.array(STRING)
    @Shared TypeMirror listOfString = FakeType.declared(listElement, STRING)
    @Shared TypeMirror listOfInteger = FakeType.declared(listElement, INTEGER)
    @Shared TypeMirror optionalOfString = FakeType.declared(optionalElement, STRING)
    @Shared TypeMirror memberFixture = FakeType.declared(memberFixtureElement)
    @Shared TypeMirror memberFixtureBase = FakeType.declared(memberFixtureBaseElement)
    @Shared TypeMirror objectType = FakeType.declared(objectElement)
    @Shared TypeMirror dayOfWeek = FakeType.declared(dayOfWeekElement)

    def setupSpec() {
        stringElement.simpleName >> new FakeName('String')
        stringElement.qualifiedName >> new FakeName('java.lang.String')
        stringElement.kind >> ElementKind.CLASS
        stringElement.superclass >> objectType
        integerElement.asType() >> INTEGER
        listElement.asType() >> FakeType.declared(listElement)
        optionalElement.asType() >> FakeType.declared(optionalElement)
        collectionElement.asType() >> FakeType.declared(collectionElement)
        iterableElement.asType() >> FakeType.declared(iterableElement)
        objectElement.superclass >> FakeType.marker(TypeKind.NONE)
        memberFixtureElement.superclass >> memberFixtureBase
        dayOfWeekElement.kind >> ElementKind.ENUM

        ctx.named('java.lang.String', stringElement)
        ctx.named('java.lang.Integer', integerElement)
        ctx.named('java.util.List', listElement)
        ctx.named('java.util.Optional', optionalElement)
        ctx.named('java.util.Collection', collectionElement)
        ctx.named('java.lang.Iterable', iterableElement)
        ctx.assignable(FakeType.declared(listElement), FakeType.declared(collectionElement))
        ctx.assignable(FakeType.declared(listElement), FakeType.declared(iterableElement))

        fieldMember.simpleName >> new FakeName('field')
        fieldMember.kind >> ElementKind.FIELD
        fieldMember.modifiers >> ([] as Set)

        privateFieldMember.simpleName >> new FakeName('privateField')
        privateFieldMember.kind >> ElementKind.FIELD
        privateFieldMember.modifiers >> ([Modifier.PRIVATE] as Set)

        staticFieldMember.simpleName >> new FakeName('staticField')
        staticFieldMember.kind >> ElementKind.FIELD
        staticFieldMember.modifiers >> ([Modifier.STATIC] as Set)

        methodMember.simpleName >> new FakeName('method')
        methodMember.kind >> ElementKind.METHOD
        methodMember.modifiers >> ([] as Set)

        toStringMember.simpleName >> new FakeName('toString')
        toStringMember.kind >> ElementKind.METHOD
        toStringMember.modifiers >> ([] as Set)

        ctorMember.kind >> ElementKind.CONSTRUCTOR
        ctorMember.modifiers >> ([] as Set)

        ctx.members(memberFixtureElement,
                [fieldMember, privateFieldMember, staticFieldMember, methodMember, toStringMember, ctorMember])
    }

    def 'isSameType and isAssignable delegate to Types'() {
        expect:
        ctx.isSameType(STRING, STRING)
        !ctx.isSameType(STRING, INT)
        ctx.isAssignable(listOfString, listOfString)
        !ctx.isAssignable(STRING, INT)
    }

    def 'erasure returns the raw type of a generic declaration'() {
        expect:
        ctx.isSameType(ctx.erasure(listOfString), ctx.erasure(listOfInteger))
    }

    def 'kind/isPrimitive/isArray/isDeclared/isTypeVariable classify a type'() {
        expect:
        ctx.kind(INT) == TypeKind.INT
        ctx.isPrimitive(INT)
        !ctx.isPrimitive(STRING)
        ctx.isArray(stringArray)
        !ctx.isArray(STRING)
        ctx.isDeclared(STRING)
        !ctx.isDeclared(INT)
        !ctx.isTypeVariable(STRING)
    }

    def 'typeArgument/typeArgumentCount read the generic arguments of a declared type'() {
        expect:
        ctx.isSameType(ctx.typeArgument(listOfString, 0), STRING)
        ctx.typeArgumentCount(listOfString) == 1
    }

    def 'typeArgument/typeArgumentCount throw on a non-declared type'() {
        when:
        ctx.typeArgument(INT, 0)
        then:
        thrown(IllegalArgumentException)

        when:
        ctx.typeArgumentCount(INT)
        then:
        thrown(IllegalArgumentException)
    }

    def 'typeArgument throws on an out-of-bounds index'() {
        when:
        ctx.typeArgument(listOfString, 5)
        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'arrayComponent reads the element type of an array, and rejects non-arrays'() {
        expect:
        ctx.isSameType(ctx.arrayComponent(stringArray), STRING)

        when:
        ctx.arrayComponent(STRING)
        then:
        thrown(IllegalArgumentException)
    }

    def 'declaredType/arrayType construct a concrete type over an element/component'() {
        expect:
        ctx.isSameType(ctx.declaredType(listElement, STRING), listOfString)
        ctx.isSameType(ctx.arrayType(STRING), stringArray)
    }

    def 'boxed/unboxed convert between a primitive and its wrapper'() {
        expect:
        ctx.isSameType(ctx.boxed(INT), INTEGER)
        ctx.isSameType(ctx.unboxed(INTEGER), INT)
    }

    def 'primitiveType builds the primitive mirror for a TypeKind'() {
        expect:
        ctx.isSameType(ctx.primitiveType(TypeKind.INT), INT)
    }

    def 'simpleName reads a declared type\'s simple name, else its text form'() {
        expect:
        ctx.simpleName(STRING) == 'String'
        ctx.simpleName(INT) == 'int'
    }

    def 'qualifiedName reads a declared type\'s fully-qualified name, else its text form'() {
        expect:
        ctx.qualifiedName(STRING) == 'java.lang.String'
        ctx.qualifiedName(INT) == 'int'
    }

    def 'asTypeElement resolves the backing element of a declared type, and is empty otherwise'() {
        expect:
        ctx.asTypeElement(STRING).present
        !ctx.asTypeElement(INT).present
    }

    def 'typeElementNamed resolves a type by FQN, or null when unresolvable'() {
        expect:
        ctx.typeElementNamed('java.lang.String') != null
        ctx.typeElementNamed('not.a.real.Type') == null
    }

    def 'superclassOf reads the direct superclass, and NONE when there is none'() {
        expect:
        ctx.isSameType(ctx.superclassOf(memberFixture), memberFixtureBase)
        ctx.superclassOf(STRING).kind != TypeKind.NONE
        ctx.superclassOf(objectType).kind == TypeKind.NONE
    }

    def 'isList/isSet/isOptional/isStream/isCollection/isIterable/isType classify container kinds'() {
        expect:
        ctx.isList(listOfString)
        !ctx.isList(STRING)
        ctx.isOptional(optionalOfString)
        !ctx.isOptional(STRING)
        ctx.isCollection(listOfString)
        !ctx.isCollection(STRING)
        ctx.isIterable(listOfString)
        !ctx.isIterable(STRING)
        ctx.isType(listOfString, 'java.util.List')
        !ctx.isType(INT, 'java.util.List')
    }

    def 'isEnum recognises an enum declaration and rejects others'() {
        expect:
        ctx.isEnum(dayOfWeek)
        !ctx.isEnum(STRING)
    }

    def 'isReferenceType accepts declared/array/type-variable kinds and rejects primitives'() {
        expect:
        ctx.isReferenceType(STRING)
        ctx.isReferenceType(stringArray)
        !ctx.isReferenceType(INT)
    }

    def 'membersOf lists a type\'s members, including inherited ones'() {
        when:
        def members = ctx.membersOf(memberFixtureElement).toList()

        then:
        members.any { it.simpleName.contentEquals('field') }
        members.any { it.simpleName.contentEquals('toString') }
    }

    def 'isField/isMethod/isConstructor/isPrivate/isStatic classify a member'() {
        expect:
        ctx.isField(fieldMember)
        !ctx.isField(methodMember)
        ctx.isMethod(methodMember)
        !ctx.isMethod(fieldMember)
        ctx.isConstructor(ctorMember)
        !ctx.isConstructor(fieldMember)
        ctx.isPrivate(privateFieldMember)
        !ctx.isPrivate(fieldMember)
        ctx.isStatic(staticFieldMember)
        !ctx.isStatic(fieldMember)
    }
}
