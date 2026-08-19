package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Modifier

/**
 * {@link MemberVisibility} is the access modifier the generate stage puts on every strategy-requested class
 * member (design D3 of change {@code add-setter-assembly}). Each constant's lower-case name is the
 * {@code percolate.helpers.visibility} token it answers to.
 */
@Tag('unit')
class MemberVisibilitySpec extends Specification {

    def 'each constant answers to its own lower-case name'() {
        expect:
        MemberVisibility.PRIVATE.token() == 'private'
        MemberVisibility.PACKAGE.token() == 'package'
        MemberVisibility.PROTECTED.token() == 'protected'
        MemberVisibility.PUBLIC.token() == 'public'
    }

    def 'each constant contributes its own access modifier, and package contributes none'() {
        expect:
        MemberVisibility.PRIVATE.modifiers() == [Modifier.PRIVATE] as Set
        MemberVisibility.PACKAGE.modifiers() == [] as Set
        MemberVisibility.PROTECTED.modifiers() == [Modifier.PROTECTED] as Set
        MemberVisibility.PUBLIC.modifiers() == [Modifier.PUBLIC] as Set
    }
}
