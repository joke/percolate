package io.github.joke.percolate.processor.internal.stages.discover

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier

import static javax.lang.model.element.Modifier.ABSTRACT
import static javax.lang.model.element.Modifier.PUBLIC

/**
 * {@link AbstractMethodFilter} unit-tested on plain {@link AbstractMethodDescriptor}s: it keeps the abstract,
 * non-{@code Object} methods — the ones a mapper must implement — and returns their opaque {@link ExecutableElement}
 * tokens. No javac substrate: the tokens are bare {@code Mock()}s, never stubbed.
 */
@Tag('unit')
class AbstractMethodFilterSpec extends Specification {

    AbstractMethodFilter filter = new AbstractMethodFilter()

    def 'keeps abstract non-Object methods, dropping concrete methods and abstract Object methods'() {
        ExecutableElement abstractMethod = Mock()
        ExecutableElement concreteMethod = Mock()
        ExecutableElement abstractObjectMethod = Mock()
        def descriptors = [
                descriptor([ABSTRACT] as Set, false, abstractMethod),
                descriptor([] as Set, false, concreteMethod),
                descriptor([ABSTRACT] as Set, true, abstractObjectMethod),
        ]

        expect:
        filter.abstractMethods(descriptors) == [abstractMethod]
    }

    def 'isAbstract reflects only the ABSTRACT modifier'() {
        ExecutableElement method = Mock()

        expect:
        filter.isAbstract(descriptor(modifiers, false, method)) == expected

        where:
        modifiers               | expected
        ([ABSTRACT] as Set)     | true
        ([] as Set)             | false
        ([PUBLIC] as Set)       | false
        ([PUBLIC, ABSTRACT] as Set) | true
    }

    private AbstractMethodDescriptor descriptor(
            final Set<Modifier> modifiers, final boolean enclosingIsObject, final ExecutableElement method) {
        new AbstractMethodDescriptor(modifiers, enclosingIsObject, method)
    }
}
