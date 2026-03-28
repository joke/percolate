package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class PipelineSpec extends Specification {

    Pipeline pipeline = new Pipeline()

    def 'process() returns null for any element'() {
        given:
        def element = Mock(TypeElement)

        expect:
        pipeline.process(element) == null
    }
}
