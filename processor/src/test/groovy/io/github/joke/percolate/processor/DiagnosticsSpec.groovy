package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.tools.Diagnostic

@Tag('unit')
class DiagnosticsSpec extends Specification {

    Messager messager = Mock()

    def 'error forwards to Messager with element, mirror, and value'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def element = Mock(Element)
        def mirror = Mock(AnnotationMirror)
        def value = Mock(AnnotationValue)

        when:
        diagnostics.error(element, mirror, value, 'test error')

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, 'test error', element, mirror, value)
    }

    def 'error without mirror uses null for mirror and value'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def element = Mock(Element)

        when:
        diagnostics.error(element, 'test error')

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, 'test error', element, null, null)
    }

    def 'hasErrorsFor returns true after error on element'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def element = Mock(Element)

        when:
        diagnostics.error(element, 'error')

        then:
        diagnostics.hasErrorsFor(element)
    }

    def 'hasErrorsFor returns true for enclosing type when error is on contained method'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def method = Mock(Element)
        def type = Mock(Element)
        method.getEnclosingElement() >> type
        type.getEnclosingElement() >> null

        when:
        diagnostics.error(method, 'error')

        then:
        diagnostics.hasErrorsFor(type)
    }

    def 'hasErrorsFor returns false for sibling elements'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def method1 = Mock(Element)
        def method2 = Mock(Element)
        def type = Mock(Element)
        method1.getEnclosingElement() >> type
        method2.getEnclosingElement() >> type
        type.getEnclosingElement() >> null

        when:
        diagnostics.error(method1, 'error')

        then:
        !diagnostics.hasErrorsFor(method2)
    }

    def 'reset clears scarring state'() {
        given:
        def diagnostics = new Diagnostics(messager)
        def element = Mock(Element)

        when:
        diagnostics.error(element, 'error')
        diagnostics.reset()

        then:
        !diagnostics.hasErrorsFor(element)
    }
}
