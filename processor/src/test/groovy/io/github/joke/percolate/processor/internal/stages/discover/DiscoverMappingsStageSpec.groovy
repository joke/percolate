package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.Bind
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.DirectiveReader
import io.github.joke.percolate.spi.DirectiveSink
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link DiscoverMappingsStage} glue, unit-tested mock-only: the stage runs every registered
 * {@link DirectiveReader} against a fresh {@link DirectiveSinkImpl} per method (design D7 of change
 * {@code decouple-engine-from-strategy-semantics}) and installs the resulting {@code MethodDirectives} plus a
 * per-method-scope {@code GoalSpec} on the context. The collaborators are mocked; a reader's own
 * {@code javax.lang.model} reading is covered by the compile-based feature-e2e layer — no javac substrate here.
 */
@Tag('unit')
class DiscoverMappingsStageSpec extends Specification {

    DirectiveReader readerA = Mock()
    DirectiveReader readerB = Mock()
    DiscoverMappingsStage stage = new DiscoverMappingsStage([readerA, readerB])

    def 'readMethod runs every reader against the same sink'() {
        ExecutableElement method = Mock()
        TypeElement mapperType = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        def result = stage.readMethod(method, ctx)

        then:
        1 * readerA.read(method, _ as DirectiveSink) >> { m, DirectiveSink sink -> sink.bind(['first'], [], Subjects.none()) }
        1 * readerB.read(method, _ as DirectiveSink)
        0 * _

        expect:
        result.method.is(method)
        result.binds == [new Bind(['first'], [], Subjects.none())]
        ctx.diagnostics.empty
    }

    def 'readMethod reports what a reader rejects, verbatim and permanent'() {
        ExecutableElement method = Mock()
        TypeElement mapperType = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        def result = stage.readMethod(method, ctx)

        then:
        1 * readerA.read(method, _ as DirectiveSink) >> { m, DirectiveSink sink ->
            sink.reject(Subjects.none(), 'malformed on the reader\'s own terms')
        }
        1 * readerB.read(method, _ as DirectiveSink)
        0 * _

        expect:
        result.binds.empty
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message == 'malformed on the reader\'s own terms'
        }
    }

    def 'run installs the goal spec, reachable by the method scope and declaring the child'() {
        TypeElement mapperType = Mock()
        ExecutableElement method = Mock()
        def ctx = new MapperContext(mapperType)
        ctx.shape = new MapperShape(mapperType, [method])

        when:
        stage.run(ctx)

        then:
        1 * readerA.read(method, _ as DirectiveSink) >> { m, DirectiveSink sink -> sink.bind(['first'], [], Subjects.none()) }
        1 * readerB.read(method, _ as DirectiveSink)
        0 * _

        expect: 'the goal spec is reachable by the method scope and declares the child'
        ctx.methodDirectives != null
        ctx.methodDirectives*.method == [method]
        def goal = ctx.goalSpecs[new MethodScope(method)]
        goal != null
        goal.declaredChildren('') == ['first'] as Set
        goal.bindingFor('first').present
    }

    def 'run is a no-op when discovery produced no shape'() {
        TypeElement mapperType = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        0 * _

        expect:
        ctx.methodDirectives == null
    }
}
