package io.github.joke.percolate.docs.mapannotation

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's @Map-annotation page. {@code AccountMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Demonstrates `source`, `constant`,
 * both `defaultValue` fallback forms ({@code requireNonNullElse} for an absent-capable reference source, and
 * {@code orElse} for an absent {@code Optional} source), and an implicit widening primitive conversion.
 */
@Tag('integration')
class MapAnnotationDocExampleSpec extends Specification {

    def 'source, constant, and a present displayName/nickname all flow through'() {
        def mapper = new AccountMapperImpl()

        when:
        def account = mapper.map(new AccountForm('acc-1', 'Ada', Optional.of('lovelace'), 500))

        then:
        account.id == 'acc-1'
        account.status == 'ACTIVE'
        account.displayName == 'Ada'
        account.nickname == 'lovelace'
    }

    def 'an absent displayName falls back via requireNonNullElse'() {
        def mapper = new AccountMapperImpl()

        expect:
        mapper.map(new AccountForm('acc-2', null, Optional.of('x'), 0)).displayName == 'unknown'
    }

    def 'an absent Optional nickname falls back via orElse'() {
        def mapper = new AccountMapperImpl()

        expect:
        mapper.map(new AccountForm('acc-3', 'Ada', Optional.empty(), 0)).nickname == 'anon'
    }

    def 'an int source widens into a long target with no conversion method'() {
        def mapper = new AccountMapperImpl()

        expect:
        mapper.map(new AccountForm('acc-4', 'Ada', Optional.of('x'), 500)).balanceCents == 500L
    }
}
