package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement

@Tag('unit')
class ValidateSourceParametersSpec extends Specification {

    Diagnostics diagnostics = Mock()

    Name paramName(String value) {
        def name = Mock(Name)
        name.toString() >> value
        name
    }

    javax.lang.model.type.TypeMirror paramType() {
        def type = Mock(javax.lang.model.type.TypeMirror)
        type.getKind() >> javax.lang.model.type.TypeKind.DECLARED
        type.toString() >> 'Person'
        type
    }

    def 'source first segment matching a parameter produces no error'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('mapHuman')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('person')
        param.asType() >> paramType()
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('firstName', 'person.first', mirror, targetValue, sourceValue)
        def methodMappings = new MethodMappings(method, [d1])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        0 * diagnostics.error(_, _, _, _)
    }

    def 'single-segment source not matching any parameter produces an error'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('mapHuman')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('person')
        param.asType() >> paramType()
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('firstName', 'first', mirror, targetValue, sourceValue)
        def methodMappings = new MethodMappings(method, [d1])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, sourceValue, "unknown source parameter 'first' in @Map on mapHuman(Person)")
    }

    def 'multi-segment source with non-matching first segment produces an error'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('mapHuman')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('person')
        param.asType() >> paramType()
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('lastName', 'lastName.value', mirror, targetValue, sourceValue)
        def methodMappings = new MethodMappings(method, [d1])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, sourceValue, "unknown source parameter 'lastName' in @Map on mapHuman(Person)")
    }

    def 'multiple directives with unknown source parameters produce one error each'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('mapHuman')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('person')
        param.asType() >> paramType()
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def sourceValue1 = Mock(AnnotationValue)
        def sourceValue2 = Mock(AnnotationValue)
        def d1 = new MappingDirective('a', 'bad1', mirror, targetValue1, sourceValue1)
        def d2 = new MappingDirective('b', 'bad2', mirror, targetValue2, sourceValue2)
        def methodMappings = new MethodMappings(method, [d1, d2])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, sourceValue1, "unknown source parameter 'bad1' in @Map on mapHuman(Person)")
        1 * diagnostics.error(method, mirror, sourceValue2, "unknown source parameter 'bad2' in @Map on mapHuman(Person)")
    }

    def 'error points at the offending source literal'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('mapHuman')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('person')
        param.asType() >> paramType()
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('firstName', 'nonexistent', mirror, targetValue, sourceValue)
        def methodMappings = new MethodMappings(method, [d1])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method, mirror, sourceValue, _)
    }

    def 'multi-method mapper only errors on offender'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method1 = Mock(ExecutableElement)
        method1.getSimpleName() >> paramName('mapHuman')
        def param1 = Mock(VariableElement)
        param1.getSimpleName() >> paramName('person')
        param1.asType() >> paramType()
        method1.getParameters() >> [param1]
        def method2 = Mock(ExecutableElement)
        method2.getSimpleName() >> paramName('mapAddress')
        def param2 = Mock(VariableElement)
        param2.getSimpleName() >> paramName('other')
        param2.asType() >> paramType()
        method2.getParameters() >> [param2]
        def mirror = Mock(AnnotationMirror)
        def targetValue1 = Mock(AnnotationValue)
        def targetValue2 = Mock(AnnotationValue)
        def sourceValue1 = Mock(AnnotationValue)
        def sourceValue2 = Mock(AnnotationValue)
        def d1a = new MappingDirective('a', 'bad', mirror, targetValue1, sourceValue1)
        def d1b = new MappingDirective('b', 'person.first', mirror, targetValue2, sourceValue2)
        def mm1 = new MethodMappings(method1, [d1a, d1b])
        def mm2 = new MethodMappings(method2, [new MappingDirective('c', 'other', mirror, targetValue1, sourceValue1)])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [mm1, mm2])

        when:
        validator.validate(mappings)

        then:
        1 * diagnostics.error(method1, mirror, sourceValue1, _)
        0 * diagnostics.error(method2, _, _, _)
    }

    def 'generic method with type variable shows type variable name'() {
        given:
        def validator = new ValidateSourceParameters(diagnostics)
        def method = Mock(ExecutableElement)
        method.getSimpleName() >> paramName('map')
        def param = Mock(VariableElement)
        param.getSimpleName() >> paramName('source')
        def typeVar = Mock(javax.lang.model.type.TypeMirror)
        typeVar.getKind() >> javax.lang.model.type.TypeKind.TYPEVAR
        typeVar.toString() >> 'T'
        param.asType() >> typeVar
        method.getParameters() >> [param]
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)
        def d1 = new MappingDirective('field', 'source.field', mirror, targetValue, sourceValue)
        def methodMappings = new MethodMappings(method, [d1])
        def mappings = new MapperMappings(Mock(javax.lang.model.element.TypeElement), [methodMappings])

        when:
        validator.validate(mappings)

        then:
        0 * diagnostics.error(_, _, _, _)
    }
}
