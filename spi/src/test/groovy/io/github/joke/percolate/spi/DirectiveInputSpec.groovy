package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link DirectiveInput}'s two shapes. A <b>scalar</b> input carries a literal and no named parts; a
 * <b>structured</b> one carries named parts and no literal. Both keep the opaque {@link Subject} they were built
 * with — a reader captures it while parsing the annotation member and a strategy only ever hands it back.
 */
@Tag('unit')
class DirectiveInputSpec extends Specification {

    Subject subject = Mock()

    def 'scalar carries its literal, its key and its subject, and has no named parts'() {
        when:
        def input = DirectiveInput.scalar('format', 'yyyy-MM-dd', subject)

        then:
        0 * _

        expect:
        verifyAll(input) {
            key == 'format'
            value.get() == 'yyyy-MM-dd'
            members == [:]
            getSubject().is(subject)
        }
    }

    def 'structured carries its named parts, its key and its subject, and has no literal'() {
        when:
        def input = DirectiveInput.structured('enum', [source: 'A', target: 'B'], subject)

        then:
        0 * _

        expect:
        verifyAll(input) {
            key == 'enum'
            !value.present
            members == [source: 'A', target: 'B']
            getSubject().is(subject)
        }
    }

    // The parts are copied, so a caller that keeps mutating the map it passed cannot reach back into the input.
    def 'structured copies the parts it is given'() {
        def parts = [source: 'A']

        when:
        def input = DirectiveInput.structured('enum', parts, subject)
        parts.target = 'B'

        then:
        0 * _

        expect:
        input.members == [source: 'A']
    }

    def 'member reads one named part of a structured input'() {
        def input = DirectiveInput.structured('enum', [source: 'A', target: 'B'], subject)

        when:
        def result = input.member('target')

        then:
        0 * _

        expect:
        result.get() == 'B'
    }

    def 'member is empty for a part that was not written'() {
        def input = DirectiveInput.structured('enum', [source: 'A'], subject)

        when:
        def result = input.member('target')

        then:
        0 * _

        expect:
        !result.present
    }

    def 'member is always empty on a scalar input'() {
        def input = DirectiveInput.scalar('format', 'yyyy-MM-dd', subject)

        when:
        def result = input.member('format')

        then:
        0 * _

        expect:
        !result.present
    }
}
