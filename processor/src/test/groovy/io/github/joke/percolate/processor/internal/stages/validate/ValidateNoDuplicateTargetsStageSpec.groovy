package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.Bind
import io.github.joke.percolate.processor.model.MethodDirectives
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link ValidateNoDuplicateTargetsStage} seam, unit-tested directly: bindings are grouped by target path and every
 * binding after the first on a shared target is reported as a permanent diagnostic (positioned at that binding's own
 * {@link io.github.joke.percolate.spi.Subject}, design D14) — a property of the sink, regardless of which reader
 * declared them. The first occurrence is spared.
 */
@Tag('unit')
class ValidateNoDuplicateTargetsStageSpec extends Specification {

    @Subject
    def stage = new ValidateNoDuplicateTargetsStage()

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def method = Mock(ExecutableElement)

    def 'a duplicate target is flagged once at the second binding; the first is spared'() {
        when:
        stage.validate(directives(bind('status'), bind('status')), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("duplicate target 'status'")
        }
    }

    def 'distinct targets produce no diagnostic'() {
        when:
        stage.validate(directives(bind('first'), bind('second')), ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'three bindings on one target flag the two later ones, not the first'() {
        when:
        stage.validate(directives(bind('x'), bind('x'), bind('x')), ctx)

        then:
        ctx.diagnostics.size() == 2
        ctx.diagnostics.every { it.permanent }
    }

    def 'run does nothing when the context has no method directives'() {
        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'run validates every method installed on the context'() {
        given:
        ctx.methodDirectives = [directives(bind('status'), bind('status'))]

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("duplicate target 'status'")
    }

    def 'groupByTarget buckets bindings by their dotted target path'() {
        when:
        def grouped = stage.groupByTarget([bind('a'), bind('a'), bind('b')])

        then:
        grouped.keySet() == ['a', 'b'] as Set
        grouped['a'].size() == 2
        grouped['b'].size() == 1
    }

    private static Bind bind(final String target) {
        new Bind([target], [], Subjects.none())
    }

    private MethodDirectives directives(final Bind... binds) {
        new MethodDirectives(method, binds as List, [:], [], [:])
    }
}
