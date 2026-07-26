package io.github.joke.percolate.processor

import com.google.common.collect.ImmutableSetMultimap
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements

/**
 * {@link MapperStep} seam, unit-tested directly: the single round-aware step runs the {@link Pipeline} per mapper and
 * classifies the outcome (design D14 of change {@code decouple-engine-from-strategy-semantics}) — a mapper defers
 * iff it recorded at least one error and every recorded error is transient; otherwise it is consumed and every
 * collected diagnostic is flushed immediately. On the final round, {@code flushDeferredDiagnostics} re-resolves each
 * still-deferred location by name and flushes its retained message text only — never the original {@code Element}.
 *
 * <p>Unit-tested mock-only (change {@code type-query-seam}): {@link FakeElements#simpleElement} stands in for the
 * compiled mapper {@code TypeElement}; the pipeline, emitter and elements are mocked so each branch is exercised in
 * isolation, no javac.
 */
@Tag('unit')
class MapperStepSpec extends Specification {

    static final String MAPPER_FQN = 'io.github.joke.percolate.Mapper'
    static final String PERSON_MAPPER_FQN = 'test.PersonMapper'

    Pipeline pipeline = Mock()
    DiagnosticEmitter diagnosticEmitter = Mock()
    Elements elements = Mock()
    @Subject
    MapperStep step = new MapperStep(pipeline, diagnosticEmitter, elements)

    def 'annotations exposes only the @Mapper annotation type'() {
        expect:
        step.annotations() == [MAPPER_FQN] as Set
    }

    def 'process defers a mapper whose only recorded diagnostic is a transient error'() {
        def mapper = mapperType()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan for tgt[]'))

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * pipeline.process(mapper) >> ctx
        0 * _

        expect:
        deferred == [mapper] as Set
    }

    def 'process consumes and flushes a mapper carrying a permanent error, without deferring it'() {
        def mapper = mapperType()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.error(Subjects.none(), 'duplicate target').asPermanent())

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * pipeline.process(mapper) >> ctx
        1 * diagnosticEmitter.flush(mapper, ctx.diagnostics)
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process consumes a realised mapper with no diagnostics without deferring or flushing it'() {
        def mapper = mapperType()
        def ctx = new MapperContext(mapper)

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * pipeline.process(mapper) >> ctx
        1 * diagnosticEmitter.flush(mapper, [])
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process flushes a warning-only mapper immediately, without deferring it'() {
        def mapper = mapperType()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.warning(Subjects.none(), 'heads up'))

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * pipeline.process(mapper) >> ctx
        1 * diagnosticEmitter.flush(mapper, ctx.diagnostics)
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process ignores an element that is not a type element'() {
        def method = FakeElements.method('map', Mock(TypeMirror))

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, method))

        then:
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'flushDeferredDiagnostics re-resolves each deferred location by name and flushes its retained messages'() {
        def mapper = mapperType()
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan A'))
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan B'))
        pipeline.process(mapper) >> ctx
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        TypeElement relocated = FakeElements.simpleElement('test.Person')

        when:
        step.flushDeferredDiagnostics()

        then:
        1 * elements.getTypeElement(fqn) >> relocated
        1 * diagnosticEmitter.flush(relocated) { List<Diagnostic> diagnostics ->
            diagnostics*.message == ['no plan A', 'no plan B'] && diagnostics.every { !it.permanent }
        }
        0 * _
    }

    def 'flushDeferredDiagnostics skips a deferred mapper whose location no longer resolves'() {
        def mapper = mapperType()
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan'))
        pipeline.process(mapper) >> ctx
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.flushDeferredDiagnostics()

        then:
        1 * elements.getTypeElement(fqn) >> null
        0 * _
    }

    def 'flushDeferredDiagnostics empties the deferred set so a second flush emits nothing'() {
        def mapper = mapperType()
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan'))
        pipeline.process(mapper) >> ctx
        elements.getTypeElement(fqn) >> FakeElements.simpleElement('test.Person')
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        step.flushDeferredDiagnostics()

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }

    def 'a mapper that becomes realised in a later round is dropped from the deferred set'() {
        def mapper = mapperType()
        def unsatisfied = new MapperContext(mapper)
        unsatisfied.report(Diagnostic.error(Subjects.none(), 'no plan'))
        def realised = new MapperContext(mapper)
        pipeline.process(mapper) >>> [unsatisfied, realised]
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }

    def 'a mapper that becomes scarred in a later round is dropped from the deferred set'() {
        def mapper = mapperType()
        def unsatisfied = new MapperContext(mapper)
        unsatisfied.report(Diagnostic.error(Subjects.none(), 'no plan'))
        def scarred = new MapperContext(mapper)
        scarred.report(Diagnostic.error(Subjects.none(), 'duplicate target').asPermanent())
        pipeline.process(mapper) >>> [unsatisfied, scarred]
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * diagnosticEmitter.flush(mapper, scarred.diagnostics)

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }

    private static TypeElement mapperType() {
        FakeElements.simpleElement(PERSON_MAPPER_FQN)
    }
}
