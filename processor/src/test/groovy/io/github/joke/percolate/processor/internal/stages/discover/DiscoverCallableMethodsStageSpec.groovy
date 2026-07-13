package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.spi.CallableMethods
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

/**
 * {@link DiscoverCallableMethodsStage} glue, unit-tested mock-only: the stage indexes the mapper type's members
 * through the {@link CallableMethodIndexer} and installs the {@link CallableMethodFilter}'s {@code CallableMethods}
 * view on the context. The collaborators are mocked; the {@code CandidateDescriptor} and {@code CallableMethods} are
 * opaque, never-stubbed tokens. The indexer's javac member enumeration and the filter's producing/assignability logic
 * are covered by their own specs and the compile-based feature-e2e layer — no javac substrate here.
 */
@Tag('unit')
class DiscoverCallableMethodsStageSpec extends Specification {

    CallableMethodIndexer indexer = Mock()
    CallableMethodFilter filter = Mock()
    DiscoverCallableMethodsStage stage = new DiscoverCallableMethodsStage(indexer, filter)

    def 'run indexes the mapper type and installs the filtered callable methods'() {
        TypeElement mapperType = Mock()
        CandidateDescriptor descriptor = Mock()
        CallableMethods callableMethods = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        1 * indexer.index(mapperType) >> [descriptor]
        1 * filter.filter([descriptor]) >> callableMethods
        0 * _

        expect:
        ctx.callableMethods.is(callableMethods)
    }
}
