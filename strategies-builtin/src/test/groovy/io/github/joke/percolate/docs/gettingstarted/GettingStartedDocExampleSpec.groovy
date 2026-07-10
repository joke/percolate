package io.github.joke.percolate.docs.gettingstarted

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's getting-started page. {@code PersonMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter (this module's own builtin strategies), so the
 * {@code PersonMapperImpl} instantiated here is byte-identical to what a consumer's build produces — no
 * compile-testing involved. The manual include::s this very file for source and its generated body for output.
 */
@Tag('integration')
class GettingStartedDocExampleSpec extends Specification {

    def 'the generated mapper constructs a Human directly from the Person'() {
        def mapper = new PersonMapperImpl()

        expect:
        mapper.map(new Person('Ada')).firstName == 'Ada'
    }
}
