package io.github.joke.percolate.processor.stages.validate

import io.github.joke.percolate.processor.Diagnostics
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
import javax.tools.Diagnostic

@Tag('unit')
class ValidateMappingShapeStageSpec extends Specification {

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateMappingShapeStage(diagnostics)

    def method = Mock(ExecutableElement)
    def mirror = Mock(AnnotationMirror)
    def targetV = Mock(AnnotationValue)
    def sourceV = Mock(AnnotationValue)
    def constantV = Mock(AnnotationValue)
    def defaultV = Mock(AnnotationValue)

    def 'both source and constant is rejected at the constant value and the directive is dropped'() {
        given:
        def directive = new MappingDirective('status', 'in.status', 'ACTIVE', null, mirror, targetV, sourceV, constantV, null)

        when:
        def result = stage.validate(mappings(directive))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('mutually exclusive') }, method, mirror, constantV)
        result.methods[0].directives.empty
    }

    def 'neither source nor constant is rejected at the target value and the directive is dropped'() {
        given:
        def directive = new MappingDirective('status', null, null, null, mirror, targetV, null, null, null)

        when:
        def result = stage.validate(mappings(directive))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('must declare') }, method, mirror, targetV)
        result.methods[0].directives.empty
    }

    def 'defaultValue on a constant directive is rejected at the defaultValue and the constant survives'() {
        given:
        def directive = new MappingDirective('status', null, 'ACTIVE', 'x', mirror, targetV, null, constantV, defaultV)

        when:
        def result = stage.validate(mappings(directive))

        then: 'positioned at the defaultValue literal'
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("'defaultValue' requires") }, method, mirror, defaultV)

        and: 'the directive is structurally a valid constant, so it is kept for seeding'
        result.methods[0].directives.size() == 1
    }

    def 'a defaultValue alongside a source is accepted with no error'() {
        given:
        def directive = new MappingDirective('name', 'in.name', null, 'unknown', mirror, targetV, sourceV, null, defaultV)

        when:
        def result = stage.validate(mappings(directive))

        then:
        0 * messager.printMessage(*_)
        result.methods[0].directives.size() == 1
    }

    def 'exactly one of source or constant is accepted'() {
        given:
        def constantOnly = new MappingDirective('status', null, 'ACTIVE', null, mirror, targetV, null, constantV, null)
        def sourceOnly = new MappingDirective('name', 'in.name', null, null, mirror, targetV, sourceV, null, null)

        when:
        def result = stage.validate(mappings(constantOnly, sourceOnly))

        then:
        0 * messager.printMessage(*_)
        result.methods[0].directives.size() == 2
    }

    private MapperMappings mappings(final MappingDirective... directives) {
        new MapperMappings(null, [new MethodMappings(method, directives as List)])
    }
}
