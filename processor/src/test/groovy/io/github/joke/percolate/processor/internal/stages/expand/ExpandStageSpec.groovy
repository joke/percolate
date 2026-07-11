package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * {@link ExpandStage#run} wiring, unit-tested directly: with an empty strategy set nothing lands, so this is a smoke
 * test of the seam — a fresh {@link io.github.joke.percolate.processor.internal.graph.MapperGraph} is installed on
 * the context and self-seeded — not a whole-pipeline behavioural pass (that coverage lives in the per-collaborator
 * specs and {@link ExpandStageDriverOrchestrationSpec}).
 */
@Tag('unit')
class ExpandStageSpec extends Specification {

    NullabilityResolver resolver = { TypeMirror type, def scope -> Nullability.NON_NULL } as NullabilityResolver

    def 'run installs a fresh graph on the context and self-seeds the return root'() {
        def method = mapMethod()
        def ctx = new MapperContext(Stub(TypeElement))
        ctx.shape = new MapperShape(Stub(TypeElement), [method])

        when:
        stage().run(ctx)

        then: 'the stage built the graph, installed it, and seeded the one return root'
        ctx.graph != null
        ctx.graph.returnRoots().toList().size() == 1
    }

    def 'run is a no-op when discovery produced no shape — no graph is installed'() {
        def ctx = new MapperContext(Stub(TypeElement))

        when:
        stage().run(ctx)

        then:
        ctx.graph == null
    }

    private ExecutableElement mapMethod() {
        FakeElements.method('map', Mock(TypeMirror), FakeElements.param('person', Mock(TypeMirror)))
    }

    private ExpandStage stage() {
        new ExpandStage([], [], Stub(Types), Stub(Elements), resolver,
                new ProcessorOptions(false, [] as Set, false, false, false, Optional.empty()))
    }
}
