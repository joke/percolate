package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import java.util.stream.Stream

/**
 * {@link Members} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): {@code declaredMembersOf} is a thin delegation to the seam's
 * {@code membersOf}; {@code isInObjectClass} reads only the raw {@link Element#getEnclosingElement()} structural
 * link, never a seam question. No javac.
 */
@Tag('unit')
class MembersSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement parent = Mock()

    def 'declaredMembersOf delegates to the seam membersOf'() {
        Element member = Mock()
        ctx.membersOf(parent) >> Stream.of(member)

        expect:
        Members.declaredMembersOf(parent, ctx).toList() == [member]
    }

    def 'isInObjectClass is true for a member enclosed by java.lang.Object'() {
        Element member = Mock()
        TypeElement objectElement = Mock()
        member.enclosingElement >> objectElement
        objectElement.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'java.lang.Object' }] as javax.lang.model.element.Name)

        expect:
        Members.isInObjectClass(member)
    }

    def 'isInObjectClass is false for a member enclosed by a declared, non-Object type'() {
        Element member = Mock()
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'io.example.Person' }] as javax.lang.model.element.Name)

        expect:
        !Members.isInObjectClass(member)
    }

    def 'isInObjectClass is false when the enclosing element is not a TypeElement'() {
        Element member = Mock()
        Element nonTypeEnclosing = Mock()
        member.enclosingElement >> nonTypeEnclosing

        expect:
        !Members.isInObjectClass(member)
    }

    def 'asNoArgMethod is empty when the member is not a method'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> false

        expect:
        Members.asNoArgMethod(member, ctx).empty
    }

    def 'asNoArgMethod is empty for a method declared on java.lang.Object'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> true
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'java.lang.Object' }] as javax.lang.model.element.Name)

        expect:
        Members.asNoArgMethod(member, ctx).empty
    }

    def 'asNoArgMethod is empty for a method with parameters'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> true
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'io.example.Person' }] as javax.lang.model.element.Name)
        member.parameters >> [Mock(javax.lang.model.element.VariableElement)]

        expect:
        Members.asNoArgMethod(member, ctx).empty
    }

    def 'asNoArgMethod is present for a declared, non-Object, zero-parameter method'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> true
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'io.example.Person' }] as javax.lang.model.element.Name)
        member.parameters >> []

        expect:
        Members.asNoArgMethod(member, ctx).get().is(member)
    }

    def 'noArgMethodNamed is empty when the name does not match'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> true
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'io.example.Person' }] as javax.lang.model.element.Name)
        member.parameters >> []
        member.simpleName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'getOther' }] as javax.lang.model.element.Name)

        expect:
        Members.noArgMethodNamed(member, 'getName', ctx).empty
    }

    def 'noArgMethodNamed is present when the name matches exactly'() {
        ExecutableElement member = Mock()
        ctx.isMethod(member) >> true
        member.enclosingElement >> parent
        parent.qualifiedName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'io.example.Person' }] as javax.lang.model.element.Name)
        member.parameters >> []
        member.simpleName >> ([contentEquals: { CharSequence cs -> cs.toString() == 'getName' }] as javax.lang.model.element.Name)

        expect:
        Members.noArgMethodNamed(member, 'getName', ctx).get().is(member)
    }
}
