package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.tools.Diagnostic

/**
 * {@link Diagnostics} seam, unit-tested directly: errors are printed through the {@link Messager} and remember the
 * scarred element (and its enclosing element) so {@code hasErrorsFor} can answer for both; warnings print but never
 * scar; {@code reset} forgets everything for the next round.
 */
@Tag('unit')
class DiagnosticsSpec extends Specification {

    Messager messager = Mock()
    @Subject
    Diagnostics diagnostics = new Diagnostics(messager)

    Element element = Mock()
    AnnotationMirror mirror = Mock()
    AnnotationValue value = Mock()

    def 'error prints an ERROR diagnostic with the mirror and value and scars the element'() {
        when:
        diagnostics.error(element, mirror, value, 'bad mapping')

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, 'bad mapping', element, mirror, value)
        1 * element.enclosingElement >> null
        0 * _

        expect:
        diagnostics.hasErrorsFor(element)
    }

    def 'error also makes the enclosing element answer hasErrorsFor'() {
        Element parent = Mock()
        Element unrelated = Mock()

        when:
        diagnostics.error(element, mirror, value, 'oops')

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, 'oops', element, mirror, value)
        1 * element.enclosingElement >> parent
        0 * _

        expect:
        diagnostics.hasErrorsFor(element)
        diagnostics.hasErrorsFor(parent)
        !diagnostics.hasErrorsFor(unrelated)
    }

    def 'the two-argument error delegates with a null mirror and value'() {
        when:
        diagnostics.error(element, 'simple')

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, 'simple', element, null, null)
        1 * element.enclosingElement >> null
        0 * _
    }

    def 'warning prints a WARNING diagnostic and does not scar the element'() {
        when:
        diagnostics.warning(element, 'heads up')

        then:
        1 * messager.printMessage(Diagnostic.Kind.WARNING, 'heads up', element)
        0 * _

        expect:
        !diagnostics.hasErrorsFor(element)
    }

    def 'hasErrorsFor is false for an element that was never reported'() {
        expect:
        !diagnostics.hasErrorsFor(element)
    }

    def 'reset clears both the scarred element and its enclosing record'() {
        Element parent = Mock()
        element.enclosingElement >> parent
        diagnostics.error(element, 'seed')

        when:
        diagnostics.reset()

        then:
        0 * _

        expect:
        !diagnostics.hasErrorsFor(element)
        !diagnostics.hasErrorsFor(parent)
    }
}
