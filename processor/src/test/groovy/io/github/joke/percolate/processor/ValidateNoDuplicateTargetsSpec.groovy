package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement

@Tag('unit')
class ValidateNoDuplicateTargetsSpec extends Specification {

    Diagnostics diagnostics = Mock()

    def 'two duplicates produce one error'() {
        given:
        def validator = new ValidateNoDuplicateTargets(diagnostics)
        def method = Mock(ExecutableElement)
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('name', 'a', mirror, targetValue1, sourceValue)
        def d2 = new MappingDirective('name', 'b', mirror, targetValue2, sourceValue)
        def methodMappings = new MethodMappings(method, [d1, d2])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, targetValue2, "duplicate target 'name'")
    }

    def 'three duplicates produce two errors'() {
        given:
        def validator = new ValidateNoDuplicateTargets(diagnostics)
        def method = Mock(ExecutableElement)
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def targetValue3 = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('name', 'a', mirror, targetValue1, sourceValue)
        def d2 = new MappingDirective('name', 'b', mirror, targetValue2, sourceValue)
        def d3 = new MappingDirective('name', 'c', mirror, targetValue3, sourceValue)
        def methodMappings = new MethodMappings(method, [d1, d2, d3])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, targetValue2, "duplicate target 'name'")
        1 * diagnostics.error(method, mirror, targetValue3, "duplicate target 'name'")
    }

    def 'distinct targets produce no error'() {
        given:
        def validator = new ValidateNoDuplicateTargets(diagnostics)
        def method = Mock(ExecutableElement)
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('firstName', 'a', mirror, targetValue1, sourceValue)
        def d2 = new MappingDirective('lastName', 'b', mirror, targetValue2, sourceValue)
        def methodMappings = new MethodMappings(method, [d1, d2])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        0 * diagnostics.error(_, _, _, _)
    }

    def 'multi-method mapper only errors on offender'() {
        given:
        def validator = new ValidateNoDuplicateTargets(diagnostics)
        def method1 = Mock(ExecutableElement)
        def method2 = Mock(ExecutableElement)
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1a = new MappingDirective('name', 'a', mirror, targetValue1, sourceValue)
        def d1b = new MappingDirective('name', 'b', mirror, targetValue2, sourceValue)
        def d2 = new MappingDirective('other', 'c', mirror, targetValue1, sourceValue)
        def mm1 = new MethodMappings(method1, [d1a, d1b])
        def mm2 = new MethodMappings(method2, [d2])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [mm1, mm2])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method1, mirror, targetValue2, "duplicate target 'name'")
        0 * diagnostics.error(method2, _, _, _)
    }
}
