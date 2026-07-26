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

@Tag('unit')
class ValidateMappingShapeStageSpec extends Specification {

    @Subject
    def stage = new ValidateMappingShapeStage()

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def method = Mock(ExecutableElement)
    def mirror = Mock(AnnotationMirror)
    def targetV = Mock(AnnotationValue)
    def sourceV = Mock(AnnotationValue)
    def constantV = Mock(AnnotationValue)
    def defaultV = Mock(AnnotationValue)

    def 'both source and constant is rejected at the constant value and the directive is dropped'() {
        given:
        def directive = new MappingDirective('status', 'in.status', 'ACTIVE', null, null, null, mirror, targetV, sourceV, constantV, null, null, null)

        when:
        def result = stage.validate(mappings(directive), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('mutually exclusive')
        }
        result.methods[0].directives.empty
    }

    def 'neither source nor constant is rejected at the target value and the directive is dropped'() {
        given:
        def directive = new MappingDirective('status', null, null, null, null, null, mirror, targetV, null, null, null, null, null)

        when:
        def result = stage.validate(mappings(directive), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('must declare')
        }
        result.methods[0].directives.empty
    }

    def 'defaultValue on a constant directive is rejected at the defaultValue and the constant survives'() {
        given:
        def directive = new MappingDirective('status', null, 'ACTIVE', 'x', null, null, mirror, targetV, null, constantV, defaultV, null, null)

        when:
        def result = stage.validate(mappings(directive), ctx)

        then: 'reported for the defaultValue literal'
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("'defaultValue' requires")
        }

        and: 'the directive is structurally a valid constant, so it is kept for seeding'
        result.methods[0].directives.size() == 1
    }

    def 'a defaultValue alongside a source is accepted with no error'() {
        given:
        def directive = new MappingDirective('name', 'in.name', null, 'unknown', null, null, mirror, targetV, sourceV, null, defaultV, null, null)

        when:
        def result = stage.validate(mappings(directive), ctx)

        then:
        ctx.diagnostics.empty
        result.methods[0].directives.size() == 1
    }

    def 'exactly one of source or constant is accepted'() {
        given:
        def constantOnly = new MappingDirective('status', null, 'ACTIVE', null, null, null, mirror, targetV, null, constantV, null, null, null)
        def sourceOnly = new MappingDirective('name', 'in.name', null, null, null, null, mirror, targetV, sourceV, null, null, null, null)

        when:
        def result = stage.validate(mappings(constantOnly, sourceOnly), ctx)

        then:
        ctx.diagnostics.empty
        result.methods[0].directives.size() == 2
    }

    def 'run is a no-op when the context has no mappings'() {
        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
        ctx.mappings == null
    }

    def 'run installs the validated mappings, dropping the contradictory directive'() {
        given:
        ctx.mappings = mappings(new MappingDirective('status', 'in.status', 'ACTIVE', null, null, null, mirror, targetV, sourceV,
                constantV, null, null, null))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('mutually exclusive')

        and:
        ctx.mappings.methods[0].directives.empty
    }

    private MapperMappings mappings(final MappingDirective... directives) {
        new MapperMappings(null, [new MethodMappings(method, directives as List)])
    }
}
