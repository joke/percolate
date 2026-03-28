package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.tools.Diagnostic.Kind

@Tag('unit')
class StageResultSpec extends Specification {

    def 'success result isSuccess() returns true'() {
        given:
        final result = StageResult.success('value')

        expect:
        result.isSuccess()
    }

    def 'success result value() returns the value'() {
        given:
        final result = StageResult.success('hello')

        expect:
        result.value() == 'hello'
    }

    def 'success result errors() returns empty list'() {
        given:
        final result = StageResult.success('value')

        expect:
        result.errors().isEmpty()
    }

    def 'failure result isSuccess() returns false'() {
        given:
        final diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        final result = StageResult.failure([diagnostic])

        expect:
        !result.isSuccess()
    }

    def 'failure result errors() returns diagnostics'() {
        given:
        final diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        final result = StageResult.failure([diagnostic])

        expect:
        result.errors() == [diagnostic]
    }

    def 'failure result value() throws IllegalStateException'() {
        given:
        final diagnostic = new Diagnostic(Mock(Element), 'error message', Kind.ERROR)
        final result = StageResult.failure([diagnostic])

        when:
        result.value()

        then:
        thrown(IllegalStateException)
    }
}
