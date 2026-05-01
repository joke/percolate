package io.github.joke.percolate.processor

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import io.github.joke.percolate.Mapper
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@Tag('unit')
class MapperStepSpec extends Specification {

    Pipeline pipeline = Mock()
    Diagnostics diagnostics = Mock()

    MapperStep step = new MapperStep(pipeline, diagnostics)

    def 'annotations returns @Mapper FQN'() {
        expect:
        step.annotations() == [Mapper.class.getCanonicalName()] as Set
    }

    def 'process resets Diagnostics before dispatching'() {
        given:
        def elementsByAnnotation = ImmutableSetMultimap.<String, Element>builder().build()

        when:
        step.process(elementsByAnnotation)

        then:
        1 * diagnostics.reset()
    }

    def 'process dispatches each TypeElement to Pipeline'() {
        given:
        def typeElement1 = Mock(TypeElement)
        def typeElement2 = Mock(TypeElement)
        def elementsByAnnotation = ImmutableSetMultimap.<String, Element>builder()
                .putAll(Mapper.class.getCanonicalName(), [typeElement1, typeElement2])
                .build()

        when:
        def result = step.process(elementsByAnnotation)

        then:
        1 * diagnostics.reset()
        1 * pipeline.process(typeElement1)
        1 * pipeline.process(typeElement2)
        result.isEmpty()
    }

    def 'process ignores non-TypeElement entries'() {
        given:
        def nonTypeElement = Mock(Element)
        def typeElement = Mock(TypeElement)
        def elementsByAnnotation = ImmutableSetMultimap.<String, Element>builder()
                .put(Mapper.class.getCanonicalName(), nonTypeElement)
                .put(Mapper.class.getCanonicalName(), typeElement)
                .build()

        when:
        def result = step.process(elementsByAnnotation)

        then:
        1 * pipeline.process(typeElement)
        0 * pipeline.process(nonTypeElement)
        result.isEmpty()
    }

    def 'process returns empty set'() {
        given:
        def elementsByAnnotation = ImmutableSetMultimap.<String, Element>builder().build()

        when:
        def result = step.process(elementsByAnnotation)

        then:
        result.isEmpty()
    }
}
