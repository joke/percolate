package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Modifier

/**
 * {@link HelperStyle} is the modifier policy the generate stage puts on every strategy-requested class member
 * (design D3 of change {@code add-setter-assembly}). Style only: it never changes which members are emitted.
 */
@Tag('unit')
class HelperStyleSpec extends Specification {

    def 'the default style reproduces the previous private static final field'() {
        expect:
        new HelperStyle(MemberVisibility.PRIVATE, true).fieldModifiers() as List ==
                [Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL]
    }

    def 'the visibility sets the access modifier of both kinds'() {
        expect:
        new HelperStyle(visibility, true).memberModifiers() as List == access + [Modifier.STATIC]
        new HelperStyle(visibility, true).fieldModifiers() as List == access + [Modifier.STATIC, Modifier.FINAL]

        where:
        visibility                 | access
        MemberVisibility.PRIVATE   | [Modifier.PRIVATE]
        MemberVisibility.PACKAGE   | []
        MemberVisibility.PROTECTED | [Modifier.PROTECTED]
        MemberVisibility.PUBLIC    | [Modifier.PUBLIC]
    }

    def 'switching static off drops it from both kinds, and a field stays final regardless'() {
        expect:
        new HelperStyle(MemberVisibility.PRIVATE, false).memberModifiers() as List == [Modifier.PRIVATE]
        new HelperStyle(MemberVisibility.PRIVATE, false).fieldModifiers() as List ==
                [Modifier.PRIVATE, Modifier.FINAL]
    }

    def 'the two options compose'() {
        expect:
        new HelperStyle(MemberVisibility.PUBLIC, false).memberModifiers() as List == [Modifier.PUBLIC]
    }
}
