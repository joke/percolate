package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.DirectiveSink
import io.github.joke.percolate.spi.Subject
import io.github.joke.percolate.spi.Subjects
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

    // @Map's own shape rules: source XOR constant, and defaultValue requires a source. Each violation declines the
    // whole entry — nothing is bound — and rejects the declaration positioned at the member that caused it, which is
    // where the IDE underlines. Covered here rather than only end-to-end, so the positioning is pinned per rule.
    def 'declinesShape rejects an entry declaring both a source and a constant, positioned at the constant'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        AnnotationValue target = Mock()
        AnnotationValue source = Mock()
        AnnotationValue constant = Mock()
        DirectiveSink sink = Mock()

        when:
        def declined = reader.declinesShape(method, mirror, [target: target, source: source, constant: constant], sink)

        then:
        1 * sink.reject(
                { Subject it -> positions(it, method, mirror, constant) },
                "@Map declares both 'source' and 'constant'; they are mutually exclusive")
        0 * _

        expect:
        declined
    }

    def 'declinesShape rejects an entry declaring neither a source nor a constant, positioned at the target'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        AnnotationValue target = Mock()
        DirectiveSink sink = Mock()

        when:
        def declined = reader.declinesShape(method, mirror, [target: target], sink)

        then:
        1 * sink.reject(
                { Subject it -> positions(it, method, mirror, target) },
                "@Map must declare a 'source' or a 'constant'")
        0 * _

        expect:
        declined
    }

    def 'declinesShape refuses to position a missing-source-or-constant rejection on an absent target'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        DirectiveSink sink = Mock()

        when:
        reader.declinesShape(method, mirror, [:], sink)

        then:
        thrown(NullPointerException)
        0 * _
    }

    def 'declinesShape rejects a defaultValue written without a source, positioned at the defaultValue'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        AnnotationValue target = Mock()
        AnnotationValue constant = Mock()
        AnnotationValue defaultValue = Mock()
        DirectiveSink sink = Mock()

        when:
        def declined = reader.declinesShape(
                method, mirror, [target: target, constant: constant, defaultValue: defaultValue], sink)

        then:
        1 * sink.reject(
                { Subject it -> positions(it, method, mirror, defaultValue) },
                "@Map 'defaultValue' requires a 'source'")
        0 * _

        expect:
        declined
    }

    def 'declinesShape accepts a well-shaped entry, rejecting nothing'() {
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        DirectiveSink sink = Mock()

        when:
        def declined = reader.declinesShape(method, mirror, written, sink)

        then:
        0 * _

        expect:
        !declined

        where:
        shape                       | written
        'a source alone'            | [target: Mock(AnnotationValue), source: Mock(AnnotationValue)]
        'a constant alone'          | [target: Mock(AnnotationValue), constant: Mock(AnnotationValue)]
        'a source and defaultValue' | [target: Mock(AnnotationValue), source: Mock(AnnotationValue),
                                       defaultValue: Mock(AnnotationValue)]
    }

    // A subject is opaque and compares by identity, so it is checked through the position the emitter resolves it to.
    private static boolean positions(
            final Subject subject,
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final AnnotationValue value) {
        def position = Subjects.resolve(subject, method)
        position.element.is(method) && position.mirror.is(mirror) && position.value.is(value)
    }
}
