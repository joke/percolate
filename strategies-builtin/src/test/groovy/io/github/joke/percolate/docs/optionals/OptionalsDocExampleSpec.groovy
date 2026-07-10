package io.github.joke.percolate.docs.optionals

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's Optionals chapter. {@code PreferencesMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Each method witnesses one
 * Optional mechanism: wrapping a nullable scalar, and unwrapping into both a non-null and a nullable target.
 */
@Tag('integration')
class OptionalsDocExampleSpec extends Specification {

    PreferencesMapper mapper = new PreferencesMapperImpl()

    def 'wrapBio lifts a present scalar into a present Optional'() {
        expect:
        mapper.wrapBio('hello') == Optional.of('hello')
    }

    def 'wrapBio lifts a null scalar into an empty Optional, via ofNullable'() {
        expect:
        mapper.wrapBio(null) == Optional.empty()
    }

    def 'unwrapHandle collapses a present Optional into its value'() {
        expect:
        mapper.unwrapHandle(Optional.of('ada')) == 'ada'
    }

    def 'unwrapHandle throws on an absent Optional, via orElseThrow'() {
        when:
        mapper.unwrapHandle(Optional.empty())

        then:
        thrown(NoSuchElementException)
    }

    def 'unwrapNickname collapses a present Optional into its value'() {
        expect:
        mapper.unwrapNickname(Optional.of('grace')) == 'grace'
    }

    def 'unwrapNickname collapses an absent Optional into null, via orElse(null)'() {
        expect:
        mapper.unwrapNickname(Optional.empty()) == null
    }
}
