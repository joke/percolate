package io.github.joke.percolate.spi.builtins

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement

/**
 * The two pure helpers {@link MapDirectiveReader} uses to translate a written {@code @Map} member into sink calls.
 * The reader as a whole is {@code @CoverageIgnore} and covered end-to-end, but both helpers became instance methods
 * in change {@code tighten-testability-conventions} and are exercised directly here, path-splitting edge cases
 * included — the empty path is the one that decides whether a bare {@code @Map} target reads as a root or as a
 * single empty segment.
 */
@Tag('unit')
class MapDirectiveReaderSpec extends Specification {

    MapDirectiveReader reader = new MapDirectiveReader()

    def 'splitDotted breaks a dotted path into its segments'() {
        expect:
        reader.splitDotted('address.street.number') == ['address', 'street', 'number']
    }

    def 'splitDotted returns a single segment for an undotted path'() {
        expect:
        reader.splitDotted('name') == ['name']
    }

    def 'splitDotted returns no segments at all for the empty path'() {
        expect:
        reader.splitDotted('').empty
    }

    def 'splitDotted keeps trailing empty segments rather than dropping them'() {
        expect:
        reader.splitDotted('a..b.') == ['a', '', 'b', '']
    }

    def 'toInput carries the member key and its written text, positioned at the annotation value'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        AnnotationValue value = Mock()
        value.value >> 'yyyy-MM-dd'

        expect:
        def input = reader.toInput(method, mirror, 'format', value)
        input.key == 'format'
        input.value.get() == 'yyyy-MM-dd'
        input.subject.mirror.is(mirror)
        input.subject.value.is(value)
    }
}
