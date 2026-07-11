package io.github.joke.percolate.docs.temporal

import spock.lang.Specification
import spock.lang.Tag

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Backs the manual's temporal-mapping page. {@code TemporalMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Exercises the two-hub composition,
 * the zone bridge's precedence, and {@code @Map(format = …)} parse/render round-trips.
 */
@Tag('integration')
class TemporalMappingDocExampleSpec extends Specification {

    def mapper = new TemporalMapperImpl()

    @SuppressWarnings('NoJavaUtilDate') // java.util.Date is the type under test — an auto-roster absolute spoke
    def 'a same-family spoke composes through the Instant hub with no truncation'() {
        def date = new Date(1_752_230_400_000L)

        expect:
        mapper.toInstant(date) == date.toInstant()
    }

    def 'a cross-spoke absolute pair composes through Instant, preserving the instant'() {
        // java.util.Date has only millisecond resolution, so the source instant is pinned to a millisecond boundary
        def offsetDateTime = Instant.ofEpochMilli(1_752_230_400_000L).atOffset(ZoneOffset.ofHours(3))

        when:
        def result = mapper.toDate(offsetDateTime)

        then:
        result.toInstant() == offsetDateTime.toInstant()
    }

    def 'crossing families with no zone declared defers to the runtime default zone'() {
        def instant = Instant.ofEpochMilli(1_752_230_400_000L)

        expect:
        mapper.toLocalDateTime(instant) == instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    def 'a directive-declared zone is frozen into the generated code'() {
        def instant = Instant.ofEpochMilli(1_752_230_400_000L)

        expect:
        mapper.toBerlinTime(instant) == instant.atZone(ZoneId.of('Europe/Berlin')).toLocalDateTime()
    }

    def '@Map(format) parses a String into a LocalDate via the hoisted formatter'() {
        expect:
        mapper.parseDate('2026-07-11') == LocalDate.of(2026, 7, 11)
    }

    def '@Map(format) renders a LocalDate as a String via the same hoisted formatter'() {
        expect:
        mapper.formatDate(LocalDate.of(2026, 7, 11)) == '2026-07-11'
    }
}
