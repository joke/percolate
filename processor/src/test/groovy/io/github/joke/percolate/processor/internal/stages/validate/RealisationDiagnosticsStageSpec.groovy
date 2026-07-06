package io.github.joke.percolate.processor.internal.stages.validate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScopeDecl
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link RealisationDiagnosticsStage} seam, unit-tested directly: each unreachable return-root {@code Value} (infinite
 * extraction cost over a constructed {@link MapperGraph}) is recorded on the context as a "no plan" message naming the
 * deepest unreachable demand and its type. Nothing is recorded when the graph is absent or the mapper already carries
 * errors (a targeted diagnostic already explains it).
 *
 * <p>Unit-tested mock-only: a plain {@code Stub} stands in for the compiled type, since this stage never reaches a
 * {@code ResolveCtx} and only ever prints {@code type.toString()} in its message.
 */
@Tag('unit')
class RealisationDiagnosticsStageSpec extends Specification {

    @Shared TypeMirror STRING = Stub(TypeMirror) { toString() >> 'java.lang.String' }
    @Shared TypeMirror LIST_OF_STRING = Mock(TypeMirror)

    def method = Mock(ExecutableElement) {
        getSimpleName() >> FakeElements.name('map')
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)
    def mapperType = Mock(TypeElement)
    def diagnostics = Mock(Diagnostics)
    @Subject
    def stage = new RealisationDiagnosticsStage(diagnostics)

    def 'an unreachable return root is recorded as a no-plan message naming the missing producer and type'() {
        given:
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def root = graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.markReturnRoot(root)
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.size() == 1
        ctx.unsatisfiedRealisation[0].contains('no plan for tgt[]')
        ctx.unsatisfiedRealisation[0].contains('java.lang.String')
    }

    def 'the deepest unreachable demand is descended to and named, not the return root itself'() {
        given:
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), STRING, Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('build', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', STRING, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then: 'the message blames the unreachable child demand tgt[x], not the root'
        ctx.unsatisfiedRealisation.size() == 1
        ctx.unsatisfiedRealisation[0].contains('tgt[x] has no producer')
    }

    def 'nothing is recorded when the mapper already carries errors'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> true
        def graph = new MapperGraph()
        graph.markReturnRoot(graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL)))

        when:
        def ctx = context(graph)
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'nothing is recorded when there is no graph'() {
        given:
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'a reachable return root records no message'() {
        // a root produced by a nullary (portless) operation — finite cost, so reachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def operation = graph.apply(new AddOperation('supply', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'the deepest miss on a source-path demand is labelled by its node id, not a target path'() {
        // a producer whose reachable leaf port is skipped for the unreachable multi-segment ACCESS port
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def leaf = new AddValue(scope, new SourceLocation(new AccessPath(['in'])), STRING,
                Nullability.NON_NULL)
        def access = new AddValue(scope, new SourceLocation(new AccessPath(['in', 'missing'])), STRING,
                Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('build', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false,
                [new PortBinding(new Port('a', STRING, Nullability.NON_NULL), leaf),
                 new PortBinding(new Port('b', STRING, Nullability.NON_NULL), access)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then: 'the message blames the ACCESS demand by node id, not a tgt[...] label'
        ctx.unsatisfiedRealisation[0].contains('src[in.missing]')
    }

    def 'an operation unreachable only through its child scope blames the root demand itself'() {
        // a scope-owning operation with no unsatisfied port — its child return-root is what is unreachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def childScope = new ChildScopeDecl(STRING, Nullability.NON_NULL, STRING,
                Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('map', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], new AddValue(scope, root(), LIST_OF_STRING, Nullability.NON_NULL),
                Optional.of(childScope)))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation[0].contains('no plan for tgt[]')
    }

    def 'a producer cycle among unreachable demands terminates and reports the revisited demand'() {
        // root <- opA(port p -> child); child <- opB(port q -> root): a 2-cycle, both unreachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def rootAdd = new AddValue(scope, root(), STRING, Nullability.NON_NULL)
        def childAdd = new AddValue(scope, new TargetLocation(new TargetPath(['b'])), STRING,
                Nullability.NON_NULL)
        def opA = graph.apply(new AddOperation('a', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('p', STRING, Nullability.NON_NULL), childAdd)], rootAdd,
                Optional.empty()))
        graph.apply(new AddOperation('b', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('q', STRING, Nullability.NON_NULL), rootAdd)], childAdd,
                Optional.empty()))
        graph.markReturnRoot(graph.outputOf(opA).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.size() == 1
    }

    private static TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private MapperContext context(final MapperGraph graph) {
        def ctx = new MapperContext(mapperType)
        ctx.graph = graph
        ctx
    }
}
