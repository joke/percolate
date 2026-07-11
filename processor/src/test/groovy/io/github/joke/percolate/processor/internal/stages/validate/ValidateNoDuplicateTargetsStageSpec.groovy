package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

/**
 * {@link ValidateNoDuplicateTargetsStage} seam, unit-tested directly against a mock {@link Messager}: directives are
 * grouped by target and every directive after the first on a shared target is diagnosed (positioned at that
 * directive's {@code target} value). The first occurrence is spared.
 */
@Tag('unit')
class ValidateNoDuplicateTargetsStageSpec extends Specification {

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateNoDuplicateTargetsStage(diagnostics)

    def method = Mock(ExecutableElement)
    def mirror = Mock(AnnotationMirror)
    def firstTarget = Mock(AnnotationValue)
    def secondTarget = Mock(AnnotationValue)

    def 'a duplicate target is flagged once at the second directive; the first is spared'() {
        when:
        stage.validate(mappings(directive('status', firstTarget), directive('status', secondTarget)))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("duplicate target 'status'") }, method, mirror,
                secondTarget)
        0 * messager.printMessage(Diagnostic.Kind.ERROR, _, method, mirror, firstTarget)
    }

    def 'distinct targets produce no diagnostic'() {
        when:
        stage.validate(mappings(directive('first', firstTarget), directive('second', secondTarget)))

        then:
        0 * messager.printMessage(*_)
    }

    def 'three directives on one target flag the two later ones, not the first'() {
        given:
        def thirdTarget = Mock(AnnotationValue)

        when:
        stage.validate(mappings(
                directive('x', firstTarget), directive('x', secondTarget), directive('x', thirdTarget)))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, _, method, mirror, secondTarget)
        1 * messager.printMessage(Diagnostic.Kind.ERROR, _, method, mirror, thirdTarget)
        0 * messager.printMessage(Diagnostic.Kind.ERROR, _, method, mirror, firstTarget)
    }

    def 'run does nothing when the context has no mappings'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'run validates the mappings installed on the context'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.mappings = mappings(directive('status', firstTarget), directive('status', secondTarget))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("duplicate target 'status'") }, method, mirror,
                secondTarget)
    }

    def 'groupByTarget buckets directives by their target name'() {
        when:
        def grouped = stage.groupByTarget([directive('a', firstTarget), directive('a', secondTarget),
                                           directive('b', firstTarget)])

        then:
        grouped.keySet() == ['a', 'b'] as Set
        grouped['a'].size() == 2
        grouped['b'].size() == 1
    }

    private MappingDirective directive(final String target, final AnnotationValue targetValue) {
        new MappingDirective(target, null, null, null, null, null, mirror, targetValue, null, null, null, null, null)
    }

    private MapperMappings mappings(final MappingDirective... directives) {
        new MapperMappings(null, [new MethodMappings(method, directives as List)])
    }
}
