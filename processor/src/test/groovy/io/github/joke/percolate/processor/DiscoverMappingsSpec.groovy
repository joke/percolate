package io.github.joke.percolate.processor

import io.github.joke.percolate.Map
import io.github.joke.percolate.MapList
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Elements

@Tag('unit')
class DiscoverMappingsSpec extends Specification {

    Elements elements = Mock()

    def 'createDirective creates directive with all fields'() {
        given:
        def stage = new DiscoverMappings(elements)
        def mirror = Mock(AnnotationMirror)
        def targetValue = Mock(AnnotationValue)
        def sourceValue = Mock(AnnotationValue)

        when:
        def directive = stage.createDirective('lastName', 'firsty', mirror, targetValue, sourceValue)

        then:
        directive.target == 'lastName'
        directive.source == 'firsty'
        directive.mirror == mirror
        directive.targetValue == targetValue
        directive.sourceValue == sourceValue
    }

    def 'empty mirror list produces empty directives'() {
        given:
        def stage = new DiscoverMappings(elements)

        when:
        def directives = stage.extractDirectives([])

        then:
        directives.isEmpty()
    }

    def 'unknown annotation type produces empty directives'() {
        given:
        def stage = new DiscoverMappings(elements)
        def mirror = Mock(AnnotationMirror)
        def annotationType = Mock(DeclaredType)
        def annotationElement = Mock(TypeElement)
        def name = Mock(javax.lang.model.element.Name)
        name.toString() >> 'com.example.UnknownAnnotation'

        mirror.getAnnotationType() >> annotationType
        annotationType.asElement() >> annotationElement
        elements.getBinaryName(annotationElement) >> name

        when:
        def directives = stage.extractDirectives([mirror])

        then:
        directives.isEmpty()
    }

}
