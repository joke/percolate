package io.github.joke.percolate.processor.internal.stages.dump

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class DumpPlanStageSpec extends Specification {

    TypeMirror STRING = Mock()
    MapperGraph graph = new MapperGraph()
    Scope scope = new HarnessScope('m()')

    GraphDumpWriter writer = Mock()
    @Subject
    DumpPlanStage stage = new DumpPlanStage(writer)

    def ctx = new MapperContext(Stub(TypeElement))

    def 'a mapper with no graph yet is skipped entirely'() {
        when:
        stage.run(ctx)

        then:
        0 * writer.dump(*_)
    }

    def 'the chosen producer and its target value are in-plan; an unproduced value is not'() {
        def param = graph.valueFor(scope, new SourceLocation(AccessPath.of('p')), STRING, Nullability.NON_NULL)
        def root = graph.valueFor(scope, new TargetLocation(TargetPath.of('')), STRING, Nullability.NON_NULL)
        graph.markReturnRoot(root)
        def op = graph.apply(new AddOperation('new Thing', Stub(Codegen), 1, false,
                [new PortBinding(new Port('p', STRING, Nullability.NON_NULL),
                        new AddValue(scope, new SourceLocation(AccessPath.of('p')), STRING, Nullability.NON_NULL))],
                new AddValue(scope, new TargetLocation(TargetPath.of('')), STRING, Nullability.NON_NULL),
                Optional.empty(), [] as Set, []))
        def orphan = graph.valueFor(scope, new TargetLocation(TargetPath.of('orphan')), STRING, Nullability.NON_NULL)
        ctx.graph = graph

        when:
        stage.run(ctx)

        then:
        1 * writer.dump(ctx, 'plan') { it.test(root) && it.test(op) && !it.test(orphan) && !it.test(param) }
        0 * _
    }
}
