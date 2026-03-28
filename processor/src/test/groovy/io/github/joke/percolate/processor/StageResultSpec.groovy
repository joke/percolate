package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.tools.Diagnostic.Kind

@Tag('unit')
class StageResultSpec extends Specification {

    def 'success result isSuccess() returns true'() {
        given:
        def result = StageResult.success('value')

        expect:
        result.isSuccess()
    }

    def 'success result value() returns the value'() {
        given:
        def result = StageResult.success('hello')

        expect:
        result.value() == 'hello'
    }

    def 'success result errors() returns empty list'() {
        given:
        def result = StageResult.success('value')

        expect:
        result.errors().isEmpty()
    }

    def 'failure result isSuccess() returns false'() {
        given:
        def diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        def result = StageResult.failure([diagnostic])

        expect:
        !result.isSuccess()
    }

    def 'failure result errors() returns diagnostics'() {
        given:
        def diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        def result = StageResult.failure([diagnostic])

        expect:
        result.errors() == [diagnostic]
    }

    def 'failure result value() throws IllegalStateException'() {
        given:
        def diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        def result = StageResult.failure([diagnostic])

        when:
        result.value()

        then:
        thrown(IllegalStateException)
    }
}
