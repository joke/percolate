package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class PipelineSpec extends Specification {

    DiscoverAbstractMethods discoverAbstractMethods = Mock()
    DiscoverMappings discoverMappings = Mock()
    ValidateNoDuplicateTargets validateNoDuplicateTargets = Mock()

    Pipeline pipeline = new Pipeline(discoverAbstractMethods, discoverMappings, validateNoDuplicateTargets)

    def 'process invokes three stages in order and returns null'() {
        given:
        def typeElement = Mock(TypeElement)

        when:
        def result = pipeline.process(typeElement)

        then:
        1 * discoverAbstractMethods.apply(typeElement) >> Mock(MapperShape)
        1 * discoverMappings.apply(_) >> Mock(MapperMappings)
        1 * validateNoDuplicateTargets.validate(_)
        result == null
    }
}
