package io.github.joke.percolate.processor

import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link Diagnostic} seam, unit-tested directly: transient by default (design D14 of change
 * {@code decouple-engine-from-strategy-semantics}), with {@code asPermanent()} the sole opt-out.
 */
@Tag('unit')
class DiagnosticSpec extends Specification {

    def 'error is transient by default'() {
        expect:
        !Diagnostic.error(Subjects.none(), 'oops').permanent
    }

    def 'error carries ERROR severity, its position and its message'() {
        def position = Subjects.none()

        expect:
        with(Diagnostic.error(position, 'oops')) {
            severity == Diagnostic.Severity.ERROR
            it.position.is(position)
            message == 'oops'
        }
    }

    def 'warning is transient and carries WARNING severity'() {
        expect:
        with(Diagnostic.warning(Subjects.none(), 'heads up')) {
            severity == Diagnostic.Severity.WARNING
            !permanent
        }
    }

    def 'asPermanent returns an equal diagnostic marked permanent, unchanged otherwise'() {
        def position = Subjects.none()
        def transient_ = Diagnostic.error(position, 'oops')

        when:
        def permanent = transient_.asPermanent()

        then:
        permanent.permanent
        permanent.severity == transient_.severity
        permanent.position.is(transient_.position)
        permanent.message == transient_.message
        !transient_.permanent
    }
}
