package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link ValidateNoDuplicateTargetsStage} seam, unit-tested directly: directives are grouped by target and every
 * directive after the first on a shared target is reported as a permanent diagnostic (positioned at that directive's
 * {@code target} value, design D14). The first occurrence is spared.
 */
@Tag('unit')
class ValidateNoDuplicateTargetsStageSpec extends Specification {

    @Subject
    def stage = new ValidateNoDuplicateTargetsStage()

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def method = Mock(ExecutableElement)
    def mirror = Mock(AnnotationMirror)
    def firstTarget = Mock(AnnotationValue)
    def secondTarget = Mock(AnnotationValue)

    def 'a duplicate target is flagged once at the second directive; the first is spared'() {
        when:
        stage.validate(mappings(directive('status', firstTarget), directive('status', secondTarget)), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("duplicate target 'status'")
        }
    }

    def 'distinct targets produce no diagnostic'() {
        when:
        stage.validate(mappings(directive('first', firstTarget), directive('second', secondTarget)), ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'three directives on one target flag the two later ones, not the first'() {
        given:
        def thirdTarget = Mock(AnnotationValue)

        when:
        stage.validate(mappings(
                directive('x', firstTarget), directive('x', secondTarget), directive('x', thirdTarget)), ctx)

        then:
        ctx.diagnostics.size() == 2
        ctx.diagnostics.every { it.permanent }
    }

    def 'run does nothing when the context has no mappings'() {
        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'run validates the mappings installed on the context'() {
        given:
        ctx.mappings = mappings(directive('status', firstTarget), directive('status', secondTarget))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("duplicate target 'status'")
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
