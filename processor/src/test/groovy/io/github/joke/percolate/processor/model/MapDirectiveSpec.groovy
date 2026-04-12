package io.github.joke.percolate.processor.model

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapDirectiveSpec extends Specification {

    // Task 5.1: MapDirective with and without using

    def 'constructs directive with empty using'() {
        when:
        final directive = new MapDirective('firstName', 'givenName', '', [:])

        then:
        directive.source == 'firstName'
        directive.target == 'givenName'
        directive.using == ''
        directive.options == [:]
    }

    def 'constructs directive with using method name'() {
        when:
        final directive = new MapDirective('value', 'result', 'toResult', [:])

        then:
        directive.source == 'value'
        directive.target == 'result'
        directive.using == 'toResult'
        directive.options == [:]
    }

    def 'equality holds for directives with same fields'() {
        given:
        final a = new MapDirective('src', 'tgt', 'mapper', [:])
        final b = new MapDirective('src', 'tgt', 'mapper', [:])

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def 'directives differ when using differs'() {
        given:
        final a = new MapDirective('src', 'tgt', '', [:])
        final b = new MapDirective('src', 'tgt', 'mapper', [:])

        expect:
        a != b
    }
}
