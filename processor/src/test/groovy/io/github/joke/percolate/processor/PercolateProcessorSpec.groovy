package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * {@link PercolateProcessor} seam, unit-tested directly: the lazy Dagger-component build in {@code steps()} and the
 * final-round diagnostic flush in {@code postRound(..)} are exercised without an annotation-processing run. The
 * processing environment and the private {@code component} field are set through reflection so each branch of the
 * lazy-init and the {@code processingOver && component != null} guard is reached at the seam.
 */
@Tag('unit')
class PercolateProcessorSpec extends Specification {

    @Subject
    PercolateProcessor processor = new PercolateProcessor()

    def 'steps builds the Dagger component on first call and reuses it thereafter'() {
        ProcessingEnvironment env = Stub {
            elementUtils >> Stub(Elements)
            typeUtils >> Stub(Types)
            messager >> Stub(Messager)
            filer >> Stub(Filer)
            options >> [:]
        }
        processor.init(env)
        def first = processor.steps()
        def cached = component()
        def second = processor.steps()

        expect:
        cached != null
        component().is(cached)
        first.iterator().hasNext()
        second.iterator().hasNext()
    }

    def 'postRound on the final round flushes the deferred diagnostics'() {
        ProcessorComponent component = Mock()
        MapperStep step = Mock()
        RoundEnvironment round = Mock()
        setField(PercolateProcessor, 'component', component)

        when:
        processor.postRound(round)

        then:
        1 * round.processingOver() >> true
        1 * component.mapperStep() >> step
        1 * step.flushDeferredDiagnostics()
        0 * _
    }

    def 'postRound before the final round does nothing'() {
        ProcessorComponent component = Mock()
        RoundEnvironment round = Mock()
        setField(PercolateProcessor, 'component', component)

        when:
        processor.postRound(round)

        then:
        1 * round.processingOver() >> false
        0 * _
    }

    def 'postRound on the final round with no built component does nothing'() {
        RoundEnvironment round = Mock()

        when:
        processor.postRound(round)

        then:
        1 * round.processingOver() >> true
        0 * _
    }

    private ProcessorComponent component() {
        def field = PercolateProcessor.getDeclaredField('component')
        field.accessible = true
        field.get(processor) as ProcessorComponent
    }

    private void setField(final Class<?> owner, final String name, final value) {
        def field = owner.getDeclaredField(name)
        field.accessible = true
        field.set(processor, value)
    }
}
