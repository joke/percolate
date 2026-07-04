package io.github.joke.percolate.processor

import com.google.common.collect.ImmutableSetMultimap
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements

/**
 * {@link MapperStep} seam, unit-tested directly: the single round-aware step runs the {@link Pipeline} per mapper and
 * classifies the outcome — a scarred or realised mapper is consumed, an unsatisfied one is deferred — then flushes the
 * recorded {@code no plan} diagnostics for whatever is still deferred when processing ends, re-resolving each location
 * by name. Mapper {@code TypeElement}s are read off compiled fixtures via {@link PrivateTypeUniverse} (no
 * {@code @Mapper} compile); the pipeline, diagnostics and elements are mocked so each branch is exercised in isolation.
 */
@Tag('unit')
class MapperStepSpec extends Specification {

    static final String MAPPER_FQN = 'io.github.joke.percolate.Mapper'

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    Pipeline pipeline = Mock()
    Diagnostics diagnostics = Mock()
    Elements elements = Mock()
    @Subject
    MapperStep step = new MapperStep(pipeline, diagnostics, elements)

    def setupSpec() {
        javac.of(PersonMapper)
        javac.of(Person)
        javac.of(Human)
    }

    def 'annotations exposes only the @Mapper annotation type'() {
        expect:
        step.annotations() == [MAPPER_FQN] as Set
    }

    def 'process resets the diagnostics for the round'() {
        when:
        def deferred = step.process(ImmutableSetMultimap.of())

        then:
        1 * diagnostics.reset()
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process defers a mapper whose realisation is unsatisfied'() {
        def mapper = javac.of(PersonMapper)
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['no plan for tgt[]']

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * diagnostics.reset()
        1 * pipeline.process(mapper) >> ctx
        1 * diagnostics.hasErrorsFor(mapper) >> false
        0 * _

        expect:
        deferred == [mapper] as Set
    }

    def 'process consumes a scarred mapper without deferring it'() {
        def mapper = javac.of(PersonMapper)
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['suppressed once the mapper is scarred']

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * diagnostics.reset()
        1 * pipeline.process(mapper) >> ctx
        1 * diagnostics.hasErrorsFor(mapper) >> true
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process consumes a realised mapper with an empty outcome without deferring it'() {
        def mapper = javac.of(PersonMapper)
        def ctx = new MapperContext(mapper)

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        then:
        1 * diagnostics.reset()
        1 * pipeline.process(mapper) >> ctx
        1 * diagnostics.hasErrorsFor(mapper) >> false
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'process ignores an element that is not a type element'() {
        def method = javac.of(PersonMapper).enclosedElements.find { it.kind == ElementKind.METHOD }

        when:
        def deferred = step.process(ImmutableSetMultimap.of(MAPPER_FQN, method))

        then:
        1 * diagnostics.reset()
        0 * _

        expect:
        deferred == [] as Set
    }

    def 'flushDeferredDiagnostics re-resolves each deferred location by name and emits its messages'() {
        def mapper = javac.of(PersonMapper)
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['no plan A', 'no plan B']
        pipeline.process(mapper) >> ctx
        diagnostics.hasErrorsFor(mapper) >> false
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        TypeElement relocated = javac.of(Person)

        when:
        step.flushDeferredDiagnostics()

        then:
        1 * elements.getTypeElement(fqn) >> relocated
        1 * diagnostics.error(relocated, 'no plan A')
        1 * diagnostics.error(relocated, 'no plan B')
        0 * _
    }

    def 'flushDeferredDiagnostics skips a deferred mapper whose location no longer resolves'() {
        def mapper = javac.of(PersonMapper)
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['no plan']
        pipeline.process(mapper) >> ctx
        diagnostics.hasErrorsFor(mapper) >> false
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.flushDeferredDiagnostics()

        then:
        1 * elements.getTypeElement(fqn) >> null
        0 * _
    }

    def 'flushDeferredDiagnostics empties the deferred set so a second flush emits nothing'() {
        def mapper = javac.of(PersonMapper)
        def fqn = mapper.qualifiedName.toString()
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['no plan']
        pipeline.process(mapper) >> ctx
        diagnostics.hasErrorsFor(mapper) >> false
        elements.getTypeElement(fqn) >> javac.of(Person)
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        step.flushDeferredDiagnostics()

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }

    def 'a mapper that becomes realised in a later round is dropped from the deferred set'() {
        def mapper = javac.of(PersonMapper)
        def unsatisfied = new MapperContext(mapper)
        unsatisfied.unsatisfiedRealisation = ['no plan']
        def realised = new MapperContext(mapper)
        pipeline.process(mapper) >>> [unsatisfied, realised]
        diagnostics.hasErrorsFor(mapper) >> false
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }

    def 'a mapper that becomes scarred in a later round is dropped from the deferred set'() {
        def mapper = javac.of(PersonMapper)
        def ctx = new MapperContext(mapper)
        ctx.unsatisfiedRealisation = ['no plan']
        pipeline.process(mapper) >> ctx
        diagnostics.hasErrorsFor(mapper) >>> [false, true]
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))
        step.process(ImmutableSetMultimap.of(MAPPER_FQN, mapper))

        when:
        step.flushDeferredDiagnostics()

        then:
        0 * _
    }
}
