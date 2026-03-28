package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.tools.Diagnostic.Kind

@Tag('unit')
class DiagnosticSpec extends Specification {

    def 'carries element, message, and kind'() {
        given:
        def element = Mock(Element)
        def diagnostic = new Diagnostic(element, 'something went wrong', Kind.ERROR)

        expect:
        diagnostic.element == element
        diagnostic.message == 'something went wrong'
        diagnostic.kind == Kind.ERROR
    }
}
