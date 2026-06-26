package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind

@Tag('unit')
class MembersSpec extends Specification {

    def 'declaredMembersOf enumerates accessible members of the type'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def typeElement = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonBean)

        when:
        def memberNames = []
        Members.declaredMembersOf(typeElement, ctx).each { memberNames << it.simpleName.toString() }

        then:
        memberNames.contains('getName')
        memberNames.contains('getAge')
    }

    def 'isInObjectClass distinguishes Object methods from declared-type methods'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def typeElement = TypeUniverse.of(io.github.joke.percolate.spi.builtins.fixtures.PersonBean)
        def members = []
        Members.declaredMembersOf(typeElement, ctx).each { members << it }

        when:
        def getName = members.find { it.simpleName.toString() == 'getName' && it.kind == ElementKind.METHOD }
        def toString = members.find { it.simpleName.toString() == 'toString' && it.kind == ElementKind.METHOD }

        then:
        getName != null
        toString != null
        !Members.isInObjectClass(getName)
        Members.isInObjectClass(toString)
    }
}
