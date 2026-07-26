package io.github.joke.percolate.processor

import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element

/**
 * {@link DiagnosticEmitter} seam, unit-tested directly: the sole {@link Messager} writer, resolving each
 * {@link Diagnostic}'s opaque {@code Subject} to its element/mirror/value, falling back to the mapper type for
 * {@link Subjects#none()} (design D14 of change {@code decouple-engine-from-strategy-semantics}).
 */
@Tag('unit')
class DiagnosticEmitterSpec extends Specification {

    Messager messager = Mock()
    @Subject
    DiagnosticEmitter emitter = new DiagnosticEmitter(messager)

    Element mapperType = Mock()
    Element element = Mock()
    AnnotationMirror mirror = Mock()
    AnnotationValue value = Mock()

    def 'flush emits an ERROR diagnostic positioned at its annotation subject'() {
        when:
        emitter.flush(mapperType, [Diagnostic.error(Subjects.of(element, mirror, value), 'bad mapping')])

        then:
        1 * messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, 'bad mapping', element, mirror, value)
        0 * _
    }

    def 'flush resolves Subjects.none() to the mapper type'() {
        when:
        emitter.flush(mapperType, [Diagnostic.error(Subjects.none(), 'no plan')])

        then:
        1 * messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, 'no plan', mapperType, null, null)
        0 * _
    }

    def 'flush emits a WARNING diagnostic with WARNING kind'() {
        when:
        emitter.flush(mapperType, [Diagnostic.warning(Subjects.none(), 'heads up')])

        then:
        1 * messager.printMessage(javax.tools.Diagnostic.Kind.WARNING, 'heads up', mapperType, null, null)
        0 * _
    }

    def 'flush emits every diagnostic in order'() {
        when:
        emitter.flush(mapperType, [Diagnostic.error(Subjects.none(), 'first'), Diagnostic.error(Subjects.none(), 'second')])

        then:
        1 * messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, 'first', mapperType, null, null)

        then:
        1 * messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, 'second', mapperType, null, null)
        0 * _
    }

    def 'flush emits nothing for an empty diagnostic list'() {
        when:
        emitter.flush(mapperType, [])

        then:
        0 * _
    }
}
