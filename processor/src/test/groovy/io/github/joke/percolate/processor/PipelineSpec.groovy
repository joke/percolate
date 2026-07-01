package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.internal.stages.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement

/**
 * {@link Pipeline} seam, unit-tested directly: a fresh {@link MapperContext} wrapping the mapper element is threaded
 * through every {@link Stage} in declaration order and handed back.
 */
@Tag('unit')
class PipelineSpec extends Specification {

    Stage first = Mock()
    Stage second = Mock()
    @Subject
    Pipeline pipeline = new Pipeline([first, second])

    TypeElement element = Mock()

    def 'process runs each stage in order on one context for the element and returns it'() {
        when:
        def ctx = pipeline.process(element)

        then: 'the stages run in declaration order'
        1 * first.run(_ as MapperContext)

        then:
        1 * second.run(_ as MapperContext)
        0 * _

        expect:
        ctx.mapperType.is(element)
    }
}
