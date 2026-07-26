package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element

/**
 * {@link Subjects} seam, unit-tested directly: the sole constructor and resolver of the opaque {@link Subject}
 * (design D14 of change {@code decouple-engine-from-strategy-semantics}) — {@link Subjects#of} anchors a subject on
 * an annotation member, {@link Subjects#none()} anchors on no specific token, and {@link Subjects#resolve} recovers
 * the element/mirror/value triple the emitter positions a diagnostic at, falling back to the mapper type for
 * {@code none()}.
 */
@Tag('unit')
class SubjectsSpec extends Specification {

    Element element = Mock()
    AnnotationMirror mirror = Mock()
    AnnotationValue value = Mock()
    Element mapperType = Mock()

    def 'of resolves to the given element, mirror and value'() {
        given:
        def subject = Subjects.of(element, mirror, value)

        when:
        def position = Subjects.resolve(subject, mapperType)

        then:
        position.element.is(element)
        position.mirror.is(mirror)
        position.value.is(value)
    }

    def 'of accepts a null mirror and value'() {
        given:
        def subject = Subjects.of(element, null, null)

        when:
        def position = Subjects.resolve(subject, mapperType)

        then:
        position.element.is(element)
        position.mirror == null
        position.value == null
    }

    def 'none resolves to the mapper type with no mirror or value'() {
        when:
        def position = Subjects.resolve(Subjects.none(), mapperType)

        then:
        position.element.is(mapperType)
        position.mirror == null
        position.value == null
    }

    def 'none is a single shared instance'() {
        expect:
        Subjects.none().is(Subjects.none())
    }
}
