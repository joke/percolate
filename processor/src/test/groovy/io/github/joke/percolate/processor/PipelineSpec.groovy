package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.internal.stages.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement

/**
 * {@link Pipeline} seam, unit-tested directly: a fresh {@link MapperContext} wrapping the mapper element is threaded
 * through every {@link Stage} in declaration order and handed back. A stage throwing mid-pipeline flushes whatever
 * was collected on the context so far (design D14) before the exception propagates — the only case the {@code
 * finally} acts on; the normal, non-throwing path leaves the emit-or-defer decision to {@code MapperStep}.
 */
@Tag('unit')
class PipelineSpec extends Specification {

    Stage first = Mock()
    Stage second = Mock()
    DiagnosticEmitter diagnosticEmitter = Mock()
    @Subject
    Pipeline pipeline = new Pipeline([first, second], diagnosticEmitter)

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

    def 'a stage throwing mid-pipeline flushes whatever was collected so far, then rethrows'() {
        given:
        def failure = new IllegalStateException('boom')

        when:
        pipeline.process(element)

        then:
        1 * first.run(_ as MapperContext) >> { MapperContext ctx ->
            ctx.report(io.github.joke.percolate.processor.Diagnostic.error(
                    io.github.joke.percolate.spi.Subjects.none(), 'partial'))
            throw failure
        }
        1 * diagnosticEmitter.flush(element) { it*.message == ['partial'] }
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.is(failure)
    }

    def 'a stage throwing before recording anything flushes an empty list'() {
        when:
        pipeline.process(element)

        then:
        1 * first.run(_ as MapperContext) >> { throw new IllegalStateException('boom') }
        1 * diagnosticEmitter.flush(element, [])
        0 * _
        thrown(IllegalStateException)
    }
}
