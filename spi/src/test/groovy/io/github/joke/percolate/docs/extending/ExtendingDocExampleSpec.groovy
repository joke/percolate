package io.github.joke.percolate.docs.extending

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's Extending (SPI) page. {@code PreferenceMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter plus {@code percolate-reactor} — a genuine, separately
 * shipped strategy module added exactly as a consumer would, proving a third-party strategy needs no special
 * engine wiring: it is simply discovered via {@code ServiceLoader} and its operations participate like a
 * built-in's.
 */
@Tag('integration')
class ExtendingDocExampleSpec extends Specification {

    PreferenceMapper mapper = new PreferenceMapperImpl()

    def 'a present Optional wraps into a Mono that emits the value'() {
        expect:
        mapper.wrap(Optional.of('ada')).block() == 'ada'
    }

    def 'an absent Optional wraps into an empty Mono'() {
        expect:
        mapper.wrap(Optional.empty()).blockOptional().empty
    }
}
